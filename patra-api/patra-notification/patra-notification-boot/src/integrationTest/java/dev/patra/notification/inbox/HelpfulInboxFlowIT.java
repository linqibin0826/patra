package dev.patra.notification.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.patra.interaction.api.discussion.event.CommentDeleted;
import dev.patra.interaction.api.discussion.event.CommentEdited;
import dev.patra.interaction.api.discussion.event.CommentPublished;
import dev.patra.interaction.api.post.event.PostDeleted;
import dev.patra.interaction.api.reaction.event.HelpfulMarked;
import dev.patra.interaction.api.subject.event.InteractionExchange;
import dev.patra.interaction.api.subject.event.SubjectContext;
import dev.patra.notification.support.NotificationFlowITSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

/// 有帮助提醒（PAP-144 AC 与 release spec D7）：真 PG、真 RabbitMQ，向 `interaction.events` 发 `helpful.marked`，
/// 经门户接口看「有帮助」标签与未读数。
class HelpfulInboxFlowIT extends NotificationFlowITSupport {

  private static final long AUTHOR = 101L;
  private static final long C = 102L;
  private static final long D = 103L;
  private static final long E = 104L;
  private static final long PUBLICATION = 40L;
  private static final long POST_1 = 41L;
  private static final long POST_2 = 42L;
  private static final Instant T0 = Instant.parse("2026-10-31T06:00:00Z");
  private static final String HELPFUL = "/me/notifications?tab=helpful";
  private static final String UNREAD = "/me/notifications/unread-count";
  private static final String DEAD_LETTERS = "notification.inbox.dlq";

  /// 排空队列用的哨兵评论 ID（每次一个新的）。
  private static final AtomicLong SENTINEL = new AtomicLong(90_000L);

  @Autowired private RabbitTemplate rabbit;
  @Autowired private AmqpAdmin amqpAdmin;

  /// 对象 40 是文献、41 / 42 是作者 101 的两篇经验；四个人的名片。
  @BeforeEach
  void stubDownstream() {
    stubSubjectViews(
        "{\"subjectId\":40,\"type\":\"PUBLICATION\",\"key\":\"18\",\"available\":true,"
            + "\"title\":\"标题\",\"journal\":\"Nature\",\"publishedDate\":\"2019-03\",\"ownerAnonymous\":false},"
            + post(POST_1, 55L)
            + ","
            + post(POST_2, 56L)
            + ","
            + "{\"subjectId\":43,\"type\":\"POST\",\"key\":\"57\",\"available\":true,"
            + "\"journal\":\"Lancet\",\"abbreviation\":\"Lancet\",\"publishedDate\":\"2026-02\","
            + "\"ownerId\":201,\"ownerAnonymous\":true}");
    stubPostOutlines(
        "{\"postId\":55,\"firstDecision\":\"MAJOR_REVISION\",\"finalOutcome\":\"ACCEPTED\","
            + "\"firstRoundDays\":41,\"totalDays\":96},"
            + "{\"postId\":56,\"firstDecision\":\"ACCEPT\",\"finalOutcome\":\"ACCEPTED\","
            + "\"firstRoundDays\":30,\"totalDays\":30}");
    stubSummaries(card(C, "c") + "," + card(D, "d") + "," + card(E, "e"));
  }

  /// AC1：C、D 同一天先后点 A 的投稿经验，A 只有一条，名字是 D、人数 2、未读、时间是 D 点的时刻。
  @Test
  void same_day_marks_merge_into_one_entry_named_after_latest() {
    send(postMark(UUID.randomUUID(), C, POST_1, T0));
    send(postMark(UUID.randomUUID(), D, POST_1, T0.plusSeconds(300)));
    drain();

    JsonNode items = helpfulItems();
    assertThat(items).hasSize(1);
    JsonNode item = items.get(0);
    assertThat(item.get("type").asString()).isEqualTo("POST_HELPFUL");
    assertThat(item.get("actorCount").asInt()).isEqualTo(2);
    assertThat(item.get("actor").get("userId").asString()).isEqualTo(String.valueOf(D));
    assertThat(item.get("read").asBoolean()).isFalse();
    assertThat(item.get("occurredAt").asString()).isEqualTo("2026-10-31T06:05:00Z");
    assertThat(item.get("commentId").isNull()).isTrue();
    assertThat(item.get("excerpt").asString()).isEqualTo("经验正文");
    assertThat(item.get("subject").get("key").asString()).isEqualTo("55");
    assertThat(item.get("subject").get("experience").get("finalOutcome").asString())
        .isEqualTo("ACCEPTED");
  }

  /// AC1：已读后又有新的人点 → 变回未读、排到最前（排在后建、但时间更早的另一条前面）。
  @Test
  void new_marker_after_read_moves_entry_to_top() {
    send(postMark(UUID.randomUUID(), C, POST_1, T0));
    drain();
    send(postMark(UUID.randomUUID(), C, POST_2, T0.plusSeconds(600)));
    drain();
    JsonNode before = helpfulItems();
    assertThat(before)
        .extracting(i -> i.get("subject").get("key").asString())
        .containsExactly("56", "55");
    String first = before.get(1).get("notificationId").asString();
    assertThat(status(call(HttpMethod.PUT, AUTHOR, "/me/notifications/" + first + "/read", null)))
        .isEqualTo(200);

    send(postMark(UUID.randomUUID(), E, POST_1, T0.plusSeconds(1200)));
    drain();

    JsonNode after = helpfulItems();
    assertThat(after)
        .extracting(i -> i.get("subject").get("key").asString())
        .containsExactly("55", "56");
    assertThat(after.get(0).get("read").asBoolean()).isFalse();
    assertThat(after.get(0).get("actorCount").asInt()).isEqualTo(2);
    assertThat(after.get(0).get("actor").get("userId").asString()).isEqualTo(String.valueOf(E));
  }

  /// AC2：北京时间午夜前后两次点击 → 两条。
  @Test
  void marks_across_beijing_midnight_create_two_entries() {
    send(postMark(UUID.randomUUID(), C, POST_1, Instant.parse("2026-10-30T15:59:59Z")));
    send(postMark(UUID.randomUUID(), D, POST_1, Instant.parse("2026-10-30T16:00:01Z")));
    drain();

    assertThat(helpfulItems()).extracting(i -> i.get("actorCount").asInt()).containsExactly(1, 1);
  }

  /// AC3：同一条消息投两次、同一个人取消后再点（新的事件 ID）→ 人数仍是 1、已读的不变回未读；两个事件 ID 各记一次去重。
  @Test
  void duplicate_delivery_and_remark_do_not_add_people() {
    UUID first = UUID.randomUUID();
    UUID again = UUID.randomUUID();
    send(postMark(first, C, POST_1, T0));
    send(postMark(first, C, POST_1, T0));
    drain();
    String id = helpfulItems().get(0).get("notificationId").asString();
    assertThat(status(call(HttpMethod.PUT, AUTHOR, "/me/notifications/" + id + "/read", null)))
        .isEqualTo(200);

    send(postMark(again, C, POST_1, T0.plusSeconds(60)));
    drain();

    JsonNode item = helpfulItems().get(0);
    assertThat(item.get("actorCount").asInt()).isEqualTo(1);
    assertThat(item.get("read").asBoolean()).isTrue();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM processed_event WHERE event_id IN (?, ?)",
                Long.class,
                first,
                again))
        .isEqualTo(2);
  }

  /// AC4：两条有帮助未读、没有回复和 @ → 铃铛 0、有帮助 2。
  @Test
  void bell_total_excludes_helpful_unread() {
    send(postMark(UUID.randomUUID(), C, POST_1, T0));
    send(postMark(UUID.randomUUID(), C, POST_2, T0));
    drain();

    JsonNode counts = json(call(HttpMethod.GET, AUTHOR, UNREAD, null));
    assertThat(counts.get("total").asInt()).isZero();
    assertThat(counts.get("replies").asInt()).isZero();
    assertThat(counts.get("mentions").asInt()).isZero();
    assertThat(counts.get("helpful").asInt()).isEqualTo(2);
  }

  /// 被点的评论删除：条目跟着删，有帮助未读数回到 0，附表也清空。
  @Test
  void deleting_marked_comment_removes_entry() {
    send(commentMark(UUID.randomUUID(), C, 500L, "评论正文"));
    drain();
    assertThat(helpfulItems()).hasSize(1);

    send(deleted(500L, PUBLICATION));
    drain();

    assertThat(helpfulItems()).isEmpty();
    assertThat(json(call(HttpMethod.GET, AUTHOR, UNREAD, null)).get("helpful").asInt()).isZero();
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM inbox.helpful_actor", Long.class))
        .isZero();
  }

  /// 匿名经验的作者（201）给自己经验下 A 的评论点有帮助：A 看到的是「匿名作者」，接口不给他的名片。
  @Test
  void anonymous_owner_marking_is_shown_as_anonymous_author() {
    send(
        HelpfulMarked.builder()
            .eventId(UUID.randomUUID())
            .targetType("COMMENT")
            .targetId(600L)
            .targetAuthorId(AUTHOR)
            .targetAnonymous(false)
            .userId(201L)
            .markedAt(T0)
            .subject(SubjectContext.of(43L, "POST", 201L, true))
            .targetPlainText("经验下的评论")
            .build());
    drain();

    JsonNode item = helpfulItems().get(0);
    assertThat(item.get("type").asString()).isEqualTo("COMMENT_HELPFUL");
    assertThat(item.get("actorAnonymous").asBoolean()).isTrue();
    assertThat(item.get("actor").isNull()).isTrue();
    assertThat(item.get("actorCount").asInt()).isEqualTo(1);
  }

  /// 评论删除先到、点它的消息晚到：不生成条目，也不进死信。
  @Test
  void mark_after_comment_deleted_is_dropped() {
    send(deleted(503L, PUBLICATION));
    send(commentMark(UUID.randomUUID(), C, 503L, "评论正文"));
    drain();

    assertThat(helpfulItems()).isEmpty();
    assertThat(amqpAdmin.getQueueInfo(DEAD_LETTERS).getMessageCount()).isZero();
  }

  /// 经验删除先到、点它的消息晚到：不生成条目，也不进死信。
  @Test
  void mark_after_post_deleted_is_dropped() {
    send(
        PostDeleted.builder()
            .eventId(UUID.randomUUID())
            .postId(55L)
            .subjectId(POST_1)
            .authorId(AUTHOR)
            .deletedAt(T0)
            .build());
    send(postMark(UUID.randomUUID(), C, POST_1, T0.plusSeconds(60)));
    drain();

    assertThat(helpfulItems()).isEmpty();
    assertThat(amqpAdmin.getQueueInfo(DEAD_LETTERS).getMessageCount()).isZero();
  }

  /// 评论快照已是更新的版本：有帮助事件带来的旧正文不覆盖它。
  @Test
  void helpful_text_does_not_overwrite_newer_comment_snapshot() {
    send(rootComment(500L, "原文"));
    send(edit(500L, "改后"));
    send(commentMark(UUID.randomUUID(), C, 500L, "原文"));
    drain();

    assertThat(helpfulItems().get(0).get("excerpt").asString()).isEqualTo("改后");
  }

  /// 没收到过任何事件的评论（上线前发的）被点：摘录用事件带来的正文。
  @Test
  void helpful_on_unseen_comment_shows_event_text() {
    send(commentMark(UUID.randomUUID(), C, 501L, "上线前的评论"));
    drain();

    JsonNode item = helpfulItems().get(0);
    assertThat(item.get("type").asString()).isEqualTo("COMMENT_HELPFUL");
    assertThat(item.get("commentId").asString()).isEqualTo("501");
    assertThat(item.get("excerpt").asString()).isEqualTo("上线前的评论");
  }

  /// 评论没有文字（事件正文为空）：照常一条、摘录为空，不进死信。
  @Test
  void helpful_on_textless_comment_creates_entry_without_excerpt() {
    send(commentMark(UUID.randomUUID(), C, 502L, null));
    drain();

    JsonNode items = helpfulItems();
    assertThat(items).hasSize(1);
    assertThat(items.get(0).get("excerpt").isNull()).isTrue();
    assertThat(amqpAdmin.getQueueInfo(DEAD_LETTERS).getMessageCount()).isZero();
  }

  /// 作者 101 的「有帮助」标签。
  ///
  /// @return 列表项
  private JsonNode helpfulItems() {
    return json(call(HttpMethod.GET, AUTHOR, HELPFUL, null)).get("items");
  }

  /// 发一个哨兵删除事件并等它的墓碑出现：信箱队列单线程按序处理，之前发出的都已处理完。
  private void drain() {
    long sentinel = SENTINEL.incrementAndGet();
    send(deleted(sentinel, 999L));
    await()
        .atMost(Duration.ofSeconds(10))
        .until(
            () ->
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM inbox.comment"
                            + " WHERE comment_id = ? AND deleted_at IS NOT NULL",
                        Long.class,
                        sentinel)
                    == 1);
  }

  /// 发到 interaction 交换机，路由键取事件自己的。
  ///
  /// @param event 事件
  private void send(Object event) {
    String routingKey =
        switch (event) {
          case HelpfulMarked e -> HelpfulMarked.ROUTING_KEY;
          case CommentPublished e -> CommentPublished.ROUTING_KEY;
          case CommentEdited e -> CommentEdited.ROUTING_KEY;
          case CommentDeleted e -> CommentDeleted.ROUTING_KEY;
          case PostDeleted e -> PostDeleted.ROUTING_KEY;
          default -> throw new IllegalArgumentException("不认识的事件: " + event);
        };
    rabbit.convertAndSend(InteractionExchange.NAME, routingKey, event);
  }

  /// 某人点了作者 101 的一篇经验。
  ///
  /// @param eventId 事件 ID
  /// @param actorId 点的人
  /// @param subjectId 经验的登记对象（41 → 帖子 55，42 → 帖子 56）
  /// @param markedAt 点的时刻
  /// @return 事件
  private static HelpfulMarked postMark(
      UUID eventId, long actorId, long subjectId, Instant markedAt) {
    return HelpfulMarked.builder()
        .eventId(eventId)
        .targetType("POST")
        .targetId(subjectId == POST_1 ? 55L : 56L)
        .targetAuthorId(AUTHOR)
        .targetAnonymous(false)
        .userId(actorId)
        .markedAt(markedAt)
        .subject(SubjectContext.of(subjectId, "POST", AUTHOR, false))
        .targetPlainText("经验正文")
        .build();
  }

  /// 某人点了作者 101 在文献 40 下的一条评论。
  ///
  /// @param eventId 事件 ID
  /// @param actorId 点的人
  /// @param commentId 评论
  /// @param plainText 评论正文，可空
  /// @return 事件
  private static HelpfulMarked commentMark(
      UUID eventId, long actorId, long commentId, String plainText) {
    return HelpfulMarked.builder()
        .eventId(eventId)
        .targetType("COMMENT")
        .targetId(commentId)
        .targetAuthorId(AUTHOR)
        .targetAnonymous(false)
        .userId(actorId)
        .markedAt(T0)
        .subject(SubjectContext.of(PUBLICATION, "PUBLICATION", null, false))
        .targetPlainText(plainText)
        .build();
  }

  /// 作者 101 在文献 40 下发的根评论（没有收件人，只写快照）。
  ///
  /// @param commentId 评论
  /// @param plainText 正文
  /// @return 事件
  private static CommentPublished rootComment(long commentId, String plainText) {
    return CommentPublished.builder()
        .eventId(UUID.randomUUID())
        .commentId(commentId)
        .version(1)
        .subject(SubjectContext.of(PUBLICATION, "PUBLICATION", null, false))
        .rootCommentId(commentId)
        .authorId(AUTHOR)
        .authorAnonymous(false)
        .mentionedUserIds(List.of())
        .plainText(plainText)
        .publishedAt(T0.minusSeconds(3600))
        .build();
  }

  /// 那条根评论改成第二版。
  ///
  /// @param commentId 评论
  /// @param plainText 改后的正文
  /// @return 事件
  private static CommentEdited edit(long commentId, String plainText) {
    return CommentEdited.builder()
        .eventId(UUID.randomUUID())
        .commentId(commentId)
        .version(2)
        .subject(SubjectContext.of(PUBLICATION, "PUBLICATION", null, false))
        .rootCommentId(commentId)
        .authorId(AUTHOR)
        .authorAnonymous(false)
        .mentionedUserIds(List.of())
        .addedMentionedUserIds(List.of())
        .plainText(plainText)
        .editedAt(T0.minusSeconds(1800))
        .build();
  }

  /// 一条评论被删。
  ///
  /// @param commentId 评论
  /// @param subjectId 对象
  /// @return 事件
  private static CommentDeleted deleted(long commentId, long subjectId) {
    return CommentDeleted.builder()
        .eventId(UUID.randomUUID())
        .commentId(commentId)
        .version(1)
        .subjectId(subjectId)
        .authorId(AUTHOR)
        .placeholder(false)
        .deletedAt(T0)
        .build();
  }

  /// 作者 101 的一篇经验的对象视图。
  ///
  /// @param subjectId 登记对象
  /// @param postId 帖子
  /// @return JSON
  private static String post(long subjectId, long postId) {
    return "{\"subjectId\":"
        + subjectId
        + ",\"type\":\"POST\",\"key\":\""
        + postId
        + "\",\"available\":true,\"journal\":\"J Am Heart Assoc\","
        + "\"abbreviation\":\"J Am Heart Assoc\",\"publishedDate\":\"2026-03\","
        + "\"ownerId\":101,\"ownerAnonymous\":false}";
  }

  /// identity 的名片。
  ///
  /// @param userId 用户
  /// @param username 用户名
  /// @return JSON
  private static String card(long userId, String username) {
    return "{\"userId\":"
        + userId
        + ",\"username\":\""
        + username
        + "\",\"displayName\":\""
        + username
        + "\",\"avatarPalette\":1,\"accountStatus\":\"ACTIVE\",\"emailVerified\":true,"
        + "\"profileVisible\":true}";
  }
}
