package dev.patra.notification.infra.inbox.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.notification.domain.inbox.model.aggregate.HelpfulNotification;
import dev.patra.notification.domain.inbox.model.enums.HelpfulTargetType;
import dev.patra.notification.domain.inbox.model.vo.HelpfulMark;
import dev.patra.notification.domain.inbox.model.vo.HelpfulTarget;
import dev.patra.notification.domain.inbox.model.vo.PostText;
import dev.patra.notification.domain.inbox.model.vo.SubjectOwner;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/// 有帮助合并条目的存储（PAP-144 spec 7.1）：信箱行加列、附表记点过的人、合并唯一约束与形状约束、级联删除。
class HelpfulNotificationRepositoryAdapterIT extends InboxPersistenceITSupport {

  private static final long AUTHOR = 101L;
  private static final long C = 11L;
  private static final long D = 12L;
  private static final LocalDate DAY = LocalDate.of(2026, 10, 31);
  private static final Instant T0 = Instant.parse("2026-10-31T06:00:00Z");
  private static final HelpfulTarget POST = HelpfulTarget.of(HelpfulTargetType.POST, 55L, 41L);
  private static final HelpfulTarget COMMENT =
      HelpfulTarget.of(HelpfulTargetType.COMMENT, 500L, 40L);
  private static final SubjectOwner OWNER = SubjectOwner.of(AUTHOR, false);

  @Autowired private HelpfulNotificationRepositoryAdapter helpful;
  @Autowired private NotificationRepositoryAdapter notifications;
  @Autowired private TransactionTemplate transactions;

  /// 写入后按合并键读回：人数、最近一位、时间、未读、经验正文与它的时刻、那一天；信箱行的类别、类型与写入时刻。
  @Test
  void insert_then_find_restores_entry() {
    long id = insert(HelpfulNotification.open(AUTHOR, POST, OWNER, mark(C, 0), "经验正文"));

    HelpfulNotification found = find(AUTHOR, POST, DAY).orElseThrow();

    assertThat(found.getNotificationId()).isEqualTo(id);
    assertThat(found.getActorCount()).isEqualTo(1);
    assertThat(found.getLatestActorId()).isEqualTo(C);
    assertThat(found.getOccurredAt()).isEqualTo(T0);
    assertThat(found.getReadAt()).isNull();
    assertThat(found.getPostText()).isEqualTo(PostText.of("经验正文", T0));
    assertThat(found.getDay()).isEqualTo(DAY);
    assertThat(
            jdbcTemplate.queryForMap(
                "SELECT category, type, comment_id, subject_id, created_at = ? AS created"
                    + " FROM inbox.notification WHERE notification_id = ?",
                Timestamp.from(NOW),
                id))
        .containsEntry("category", "HELPFUL")
        .containsEntry("type", "POST_HELPFUL")
        .containsEntry("comment_id", null)
        .containsEntry("subject_id", 41L)
        .containsEntry("created", true);
  }

  /// 并入新的人后写回：附表两行，信箱行换成最近一位、变未读；同一人更早的时刻写回时取较早的。
  @Test
  void update_upserts_actor_and_keeps_earliest_time() {
    insert(HelpfulNotification.open(AUTHOR, COMMENT, OWNER, mark(C, 3), null));
    jdbcTemplate.update("UPDATE inbox.notification SET read_at = ?", Timestamp.from(NOW));

    absorbAndUpdate(COMMENT, mark(D, 5));
    absorbAndUpdate(COMMENT, mark(C, 0));

    HelpfulNotification found = find(AUTHOR, COMMENT, DAY).orElseThrow();
    assertThat(found.getMarks()).isEqualTo(Map.of(C, T0, D, T0.plusSeconds(300)));
    assertThat(found.getLatestActorId()).isEqualTo(D);
    assertThat(found.getReadAt()).isNull();
    assertThat(
            jdbcTemplate.queryForMap(
                "SELECT actor_id, occurred_at = ? AS occurred FROM inbox.notification",
                Timestamp.from(T0.plusSeconds(300))))
        .containsEntry("actor_id", D)
        .containsEntry("occurred", true);
  }

  /// 不同的天、不同的内容各是一行，按合并键各自命中。
  @Test
  void different_days_and_targets_are_separate_rows() {
    insert(HelpfulNotification.open(AUTHOR, POST, OWNER, mark(C, 0), null));
    insert(
        HelpfulNotification.open(
            AUTHOR, POST, OWNER, HelpfulMark.of(C, Instant.parse("2026-10-31T16:00:00Z")), null));
    insert(HelpfulNotification.open(AUTHOR, COMMENT, OWNER, mark(C, 0), null));

    assertThat(notificationRows()).isEqualTo(3);
    assertThat(find(AUTHOR, POST, DAY)).isPresent();
    assertThat(find(AUTHOR, POST, DAY.plusDays(1))).isPresent();
    assertThat(find(AUTHOR, COMMENT, DAY)).isPresent();
    assertThat(find(AUTHOR, COMMENT, DAY.plusDays(1))).isEmpty();
    assertThat(find(202L, POST, DAY)).isEmpty();
  }

  /// 同一收件人、同一篇经验、同一天插第二行（两行评论都为空）：撞合并唯一约束。分区表上报的是分区里那份索引的名字，
  /// 所以按键列断言。
  @Test
  void merge_key_is_unique_even_without_comment_id() {
    rawInsert(1L, "HELPFUL", "POST_HELPFUL", null, "2026-10-31", T0);

    assertThatThrownBy(() -> rawInsert(2L, "HELPFUL", "POST_HELPFUL", null, "2026-10-31", T0))
        .isInstanceOf(DuplicateKeyException.class)
        .hasMessageContaining("(recipient_id, subject_id, comment_id, helpful_day)");
  }

  /// 形状约束：有帮助缺那一天、点经验却带评论、回复带经验正文都被拒。
  @Test
  void shape_checks_reject_bad_rows() {
    assertThatThrownBy(() -> rawInsert(1L, "HELPFUL", "POST_HELPFUL", null, null, T0))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_notification_helpful_day");
    assertThatThrownBy(() -> rawInsert(2L, "HELPFUL", "POST_HELPFUL", 500L, "2026-10-31", T0))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_notification_helpful_comment");
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO inbox.notification (recipient_id, notification_id, category, type,"
                        + " actor_id, actor_anonymous, subject_id, comment_id, occurred_at,"
                        + " created_at, post_plain_text)"
                        + " VALUES (101, 3, 'REPLY', 'COMMENT_REPLY', 9, false, 40, 501, now(),"
                        + " now(), '正文')"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_notification_post_text");
  }

  /// 信箱行被 PAP-143 的四种删除删掉时，附表随之清空（级联外键）。
  @Test
  void deleting_parent_rows_cascades_to_actors() {
    insert(HelpfulNotification.open(AUTHOR, COMMENT, OWNER, mark(C, 0), null));
    notifications.deleteByComment(500L);
    assertThat(actorRows()).isZero();

    insert(HelpfulNotification.open(AUTHOR, POST, OWNER, mark(C, 0), null));
    notifications.deleteBySubject(41L);
    assertThat(actorRows()).isZero();

    insert(HelpfulNotification.open(AUTHOR, POST, OWNER, mark(C, 0), null));
    notifications.deleteAllOf(AUTHOR);
    assertThat(actorRows()).isZero();

    insert(HelpfulNotification.open(AUTHOR, POST, OWNER, mark(C, 0), null));
    notifications.deleteOccurredBefore(T0.plusSeconds(1));
    assertThat(actorRows()).isZero();
    assertThat(notificationRows()).isZero();
  }

  /// 写入与写回都是两条语句（信箱行 + 附表），必须在调用方的事务里：事务外调用直接拒绝，不会留下没有附表行的条目
  /// （那样之后这个合并键每次都恢复失败、进死信）。
  @Test
  void writes_require_a_transaction() {
    HelpfulNotification entry = HelpfulNotification.open(AUTHOR, POST, OWNER, mark(C, 0), null);

    assertThatThrownBy(() -> helpful.insert(entry, NOW))
        .isInstanceOf(IllegalTransactionStateException.class);
    assertThatThrownBy(() -> helpful.update(entry, mark(C, 0)))
        .isInstanceOf(IllegalTransactionStateException.class);
    assertThat(notificationRows()).isZero();
  }

  /// 匿名经验的作者给自己经验下别人的评论点：信箱行把这一位标成匿名（列表不给名片）。
  @Test
  void anonymous_owner_as_latest_actor_is_stored_as_anonymous() {
    insert(HelpfulNotification.open(AUTHOR, COMMENT, SubjectOwner.of(C, true), mark(C, 0), null));

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT actor_anonymous FROM inbox.notification", Boolean.class))
        .isTrue();
  }

  /// 读出、并入一次点击、写回（一个事务）。
  ///
  /// @param target 内容
  /// @param mark 点击
  private void absorbAndUpdate(HelpfulTarget target, HelpfulMark mark) {
    inTransaction(
        () -> {
          HelpfulNotification entry =
              helpful.findForUpdate(AUTHOR, target, DAY, OWNER).orElseThrow();
          entry.absorb(mark, null);
          helpful.update(entry, mark);
          return null;
        });
  }

  /// 写入一条新条目（一个事务）。
  ///
  /// @param entry 条目
  /// @return ID
  private long insert(HelpfulNotification entry) {
    return inTransaction(() -> helpful.insert(entry, NOW));
  }

  /// 按合并键读（一个事务）。
  ///
  /// @param recipientId 收件人
  /// @param target 内容
  /// @param day 那一天
  /// @return 条目
  private Optional<HelpfulNotification> find(
      long recipientId, HelpfulTarget target, LocalDate day) {
    return inTransaction(() -> helpful.findForUpdate(recipientId, target, day, OWNER));
  }

  /// 在事务里跑（加锁读要求调用方有事务）。
  ///
  /// @param work 要做的事
  /// @param <T> 结果类型
  /// @return 结果
  private <T> T inTransaction(Supplier<T> work) {
    return transactions.execute(status -> work.get());
  }

  /// 直接插一行有帮助信箱行（测约束用）。
  ///
  /// @param id 提醒 ID
  /// @param category 类别
  /// @param type 类型
  /// @param commentId 评论，可空
  /// @param day 那一天，可空
  /// @param at 时间
  private void rawInsert(
      long id, String category, String type, Long commentId, String day, Instant at) {
    jdbcTemplate.update(
        "INSERT INTO inbox.notification (recipient_id, notification_id, category, type, actor_id,"
            + " actor_anonymous, subject_id, comment_id, occurred_at, created_at, helpful_day,"
            + " post_plain_text_at)"
            + " VALUES (101, ?, ?, ?, 11, false, 41, ?, ?, ?, ?::date, ?)",
        id,
        category,
        type,
        commentId,
        Timestamp.from(at),
        Timestamp.from(at),
        day,
        Timestamp.from(at));
  }

  /// 附表行数。
  ///
  /// @return 行数
  private long actorRows() {
    return jdbcTemplate.queryForObject("SELECT count(*) FROM inbox.helpful_actor", Long.class);
  }

  /// 某人在 T0 之后若干分钟点。
  ///
  /// @param actorId 点的人
  /// @param minutes 分钟
  /// @return 点击
  private static HelpfulMark mark(long actorId, long minutes) {
    return HelpfulMark.of(actorId, T0.plusSeconds(minutes * 60));
  }
}
