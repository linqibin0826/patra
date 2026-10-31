package dev.patra.notification.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.patra.interaction.api.discussion.event.CommentDeleted;
import dev.patra.interaction.api.discussion.event.CommentEdited;
import dev.patra.interaction.api.discussion.event.CommentPublished;
import dev.patra.interaction.api.post.event.PostDeleted;
import dev.patra.interaction.api.subject.event.InteractionExchange;
import dev.patra.interaction.api.subject.event.SubjectContext;
import dev.patra.notification.support.NotificationFlowITSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

/// 信箱消费 interaction 的事件（release spec D2）：真 PG、真 RabbitMQ，向交换机 `interaction.events` 发
/// `patra-interaction-api` 里的集成事件，看信箱里出现什么。
class InboxEventFlowIT extends NotificationFlowITSupport {

  private static final long AUTHOR = 9L;
  private static final long PARENT_AUTHOR = 2L;
  private static final long MENTIONED = 4L;
  private static final long POST_SUBJECT = 41L;
  private static final Instant AT = Instant.parse("2026-10-31T08:00:00Z");

  /// 信箱的死信队列。
  private static final String DEAD_LETTERS = "notification.inbox.dlq";

  @Autowired private RabbitTemplate rabbit;
  @Autowired private AmqpAdmin amqpAdmin;

  /// 评论发表：被回复的人收到「回复我的」，@ 到的人收到「@ 我的」，正文进快照。
  @Test
  void should_create_notifications_from_published_event() {
    send(CommentPublished.ROUTING_KEY, published(UUID.randomUUID(), 500L, 40L, "谢谢 <@4>"));

    awaitRows(2);
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT recipient_id || ':' || type FROM inbox.notification ORDER BY recipient_id",
                String.class))
        .containsExactly("2:COMMENT_REPLY", "4:MENTION");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT plain_text FROM inbox.comment WHERE comment_id = 500", String.class))
        .isEqualTo("谢谢 <@4>");
  }

  /// 同一事件投两次：只生成一份。
  @Test
  void should_create_once_when_delivered_twice() {
    CommentPublished event = published(UUID.randomUUID(), 500L, 40L, "谢谢 <@4>");
    send(CommentPublished.ROUTING_KEY, event);
    send(CommentPublished.ROUTING_KEY, event);
    send(CommentDeleted.ROUTING_KEY, deleted(UUID.randomUUID(), 999L, 40L));

    awaitTombstone(999L);
    assertThat(rows()).isEqualTo(2);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM processed_event WHERE event_id = ?",
                Long.class,
                event.eventId()))
        .isEqualTo(1);
  }

  /// 删除先于发表到达：留下墓碑，后到的发表不生成提醒。
  @Test
  void should_not_create_when_deleted_arrives_first() {
    send(CommentDeleted.ROUTING_KEY, deleted(UUID.randomUUID(), 500L, 40L));
    awaitTombstone(500L);

    send(CommentPublished.ROUTING_KEY, published(UUID.randomUUID(), 500L, 40L, "谢谢 <@4>"));
    send(CommentDeleted.ROUTING_KEY, deleted(UUID.randomUUID(), 998L, 40L));

    awaitTombstone(998L);
    assertThat(rows()).isZero();
  }

  /// 编辑：v3 先到、v2 晚到，正文停在 v3；新增的 @ 照样补提醒。
  @Test
  void should_keep_newer_text_when_old_edit_arrives_late() {
    send(CommentPublished.ROUTING_KEY, published(UUID.randomUUID(), 500L, 40L, "初版"));
    awaitRows(1);

    send(CommentEdited.ROUTING_KEY, edited(UUID.randomUUID(), 500L, 3, "第三版", List.of()));
    send(CommentEdited.ROUTING_KEY, edited(UUID.randomUUID(), 500L, 2, "第二版", List.of(7L)));

    awaitRows(2);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT version || ':' || plain_text FROM inbox.comment WHERE comment_id = 500",
                String.class))
        .isEqualTo("3:第三版");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT type FROM inbox.notification WHERE recipient_id = 7", String.class))
        .isEqualTo("MENTION");
  }

  /// 编辑先于发表到达：编辑写下第二版正文与新增 @ 的提醒；发表晚到时正文不动，照常补被回复的人与发表时 @ 的人。
  @Test
  void should_notify_reply_when_publish_arrives_after_edit() {
    send(CommentEdited.ROUTING_KEY, edited(UUID.randomUUID(), 500L, 2, "第二版", List.of(7L)));
    awaitRows(1);

    send(CommentPublished.ROUTING_KEY, published(UUID.randomUUID(), 500L, 40L, "谢谢 <@4>"));

    awaitRows(3);
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT recipient_id || ':' || type FROM inbox.notification ORDER BY recipient_id",
                String.class))
        .containsExactly("2:COMMENT_REPLY", "4:MENTION", "7:MENTION");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT version || ':' || plain_text FROM inbox.comment WHERE comment_id = 500",
                String.class))
        .isEqualTo("2:第二版");
  }

  /// 处理中途失败：整个事务回滚（去重记录也不留），按 1、2、4 秒重试后进死信队列；修好后重放能正常处理。
  @Test
  void should_roll_back_and_dead_letter_when_processing_fails() {
    jdbcTemplate.execute(
        "CREATE OR REPLACE FUNCTION public.test_fail_inbox() RETURNS trigger LANGUAGE plpgsql AS"
            + " $$ BEGIN IF NEW.comment_id = 600 THEN RAISE EXCEPTION 'test failure'; END IF;"
            + " RETURN NEW; END $$");
    jdbcTemplate.execute(
        "CREATE TRIGGER test_fail_inbox BEFORE INSERT ON inbox.notification"
            + " FOR EACH ROW EXECUTE FUNCTION public.test_fail_inbox()");
    CommentPublished event = published(UUID.randomUUID(), 600L, 40L, "会失败");
    try {
      send(CommentPublished.ROUTING_KEY, event);

      await()
          .atMost(Duration.ofSeconds(30))
          .until(() -> amqpAdmin.getQueueInfo(DEAD_LETTERS).getMessageCount() == 1);
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT count(*) FROM processed_event WHERE event_id = ?",
                  Long.class,
                  event.eventId()))
          .isZero();
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT count(*) FROM inbox.comment WHERE comment_id = 600", Long.class))
          .isZero();
    } finally {
      jdbcTemplate.execute("DROP TRIGGER IF EXISTS test_fail_inbox ON inbox.notification");
      jdbcTemplate.execute("DROP FUNCTION IF EXISTS public.test_fail_inbox()");
      amqpAdmin.purgeQueue(DEAD_LETTERS, false);
    }

    send(CommentPublished.ROUTING_KEY, event);
    awaitRows(1);
  }

  /// 经验删除：它下面的提醒与快照都删掉，之后它下面评论的发表不生成提醒。
  @Test
  void should_remove_notifications_under_deleted_post() {
    send(CommentPublished.ROUTING_KEY, published(UUID.randomUUID(), 500L, POST_SUBJECT, "经验下的回复"));
    awaitRows(1);

    send(
        PostDeleted.ROUTING_KEY,
        PostDeleted.builder()
            .eventId(UUID.randomUUID())
            .postId(55L)
            .subjectId(POST_SUBJECT)
            .authorId(3L)
            .deletedAt(AT)
            .build());
    await().atMost(Duration.ofSeconds(10)).until(() -> rows() == 0);
    send(CommentPublished.ROUTING_KEY, published(UUID.randomUUID(), 501L, POST_SUBJECT, "晚到的回复"));
    send(CommentDeleted.ROUTING_KEY, deleted(UUID.randomUUID(), 997L, 40L));

    awaitTombstone(997L);
    assertThat(rows()).isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM inbox.comment WHERE subject_id = ?",
                Long.class,
                POST_SUBJECT))
        .isZero();
  }

  /// 发到 interaction 的交换机。
  ///
  /// @param routingKey 路由键
  /// @param event 集成事件
  private void send(String routingKey, Object event) {
    rabbit.convertAndSend(InteractionExchange.NAME, routingKey, event);
  }

  /// 等提醒表里有这么多行。
  ///
  /// @param expected 行数
  private void awaitRows(long expected) {
    await().atMost(Duration.ofSeconds(10)).until(() -> rows() == expected);
  }

  /// 等某条评论的墓碑出现（队列单线程按序处理：它之前发出的事件都已处理完）。
  ///
  /// @param commentId 评论
  private void awaitTombstone(long commentId) {
    await()
        .atMost(Duration.ofSeconds(10))
        .until(
            () ->
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM inbox.comment"
                            + " WHERE comment_id = ? AND deleted_at IS NOT NULL",
                        Long.class,
                        commentId)
                    == 1);
  }

  /// 提醒行数。
  ///
  /// @return 行数
  private long rows() {
    return jdbcTemplate.queryForObject("SELECT count(*) FROM inbox.notification", Long.class);
  }

  /// 作者 9 在对象下回复 2 的评论。
  ///
  /// @param eventId 事件 ID
  /// @param commentId 评论
  /// @param subjectId 对象
  /// @param plainText 正文
  /// @return 事件
  private static CommentPublished published(
      UUID eventId, long commentId, long subjectId, String plainText) {
    return CommentPublished.builder()
        .eventId(eventId)
        .commentId(commentId)
        .version(1)
        .subject(SubjectContext.of(subjectId, "PUBLICATION", null, false))
        .rootCommentId(100L)
        .parentCommentId(100L)
        .parentAuthorId(PARENT_AUTHOR)
        .authorId(AUTHOR)
        .authorAnonymous(false)
        .mentionedUserIds(plainText.contains("<@4>") ? List.of(MENTIONED) : List.of())
        .plainText(plainText)
        .publishedAt(AT)
        .build();
  }

  /// 评论 500 的一次编辑。
  ///
  /// @param eventId 事件 ID
  /// @param commentId 评论
  /// @param version 改完后的版本
  /// @param plainText 改完后的正文
  /// @param added 第一次 @ 到的人
  /// @return 事件
  private static CommentEdited edited(
      UUID eventId, long commentId, int version, String plainText, List<Long> added) {
    return CommentEdited.builder()
        .eventId(eventId)
        .commentId(commentId)
        .version(version)
        .subject(SubjectContext.of(40L, "PUBLICATION", null, false))
        .rootCommentId(100L)
        .parentCommentId(100L)
        .parentAuthorId(PARENT_AUTHOR)
        .authorId(AUTHOR)
        .authorAnonymous(false)
        .mentionedUserIds(added)
        .addedMentionedUserIds(added)
        .plainText(plainText)
        .editedAt(AT.plusSeconds(version))
        .build();
  }

  /// 一条评论被删。
  ///
  /// @param eventId 事件 ID
  /// @param commentId 评论
  /// @param subjectId 对象
  /// @return 事件
  private static CommentDeleted deleted(UUID eventId, long commentId, long subjectId) {
    return CommentDeleted.builder()
        .eventId(eventId)
        .commentId(commentId)
        .version(1)
        .subjectId(subjectId)
        .authorId(AUTHOR)
        .placeholder(false)
        .deletedAt(AT)
        .build();
  }
}
