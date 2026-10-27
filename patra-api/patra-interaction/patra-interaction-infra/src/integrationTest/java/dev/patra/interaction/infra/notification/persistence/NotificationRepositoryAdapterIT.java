package dev.patra.interaction.infra.notification.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.interaction.domain.notification.model.enums.NotificationType;
import dev.patra.interaction.domain.notification.model.vo.ExcerptText;
import dev.patra.interaction.domain.notification.model.vo.NotificationDraft;
import java.sql.Timestamp;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

/// 提醒表与写端（spec 5、6、7.3、8）：分区、唯一索引、按评论刷新与标删除、已读、清理。
class NotificationRepositoryAdapterIT extends NotificationPersistenceITSupport {

  /// 32 个收件人落在不止一个分区里。
  @Test
  void should_spread_rows_over_partitions() {
    List<NotificationDraft> drafts = new ArrayList<>();
    LongStream.rangeClosed(1, 32)
        .forEach(r -> drafts.add(draft(r, NotificationType.MENTION, 500L)));

    assertThat(notifications.insertAll(drafts)).isEqualTo(32);

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(DISTINCT tableoid) FROM notification.notification", Long.class))
        .isGreaterThanOrEqualTo(2L);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notification.notification", Long.class))
        .isEqualTo(32L);
  }

  /// 同一收件人同一条评论只留第一条。
  @Test
  void should_skip_second_notification_for_same_recipient_and_comment() {
    assertThat(notifications.insertAll(List.of(draft(A, NotificationType.COMMENT_REPLY, 500L))))
        .isEqualTo(1);

    assertThat(notifications.insertAll(List.of(draft(A, NotificationType.MENTION, 500L)))).isZero();

    assertThat(
            jdbcTemplate.queryForList(
                "SELECT type FROM notification.notification WHERE recipient_id = ?",
                String.class,
                A))
        .containsExactly("COMMENT_REPLY");
  }

  /// 编辑后刷新这条评论的全部提醒；已标删除的不会被刷回。
  @Test
  void should_refresh_excerpt_for_all_recipients_of_comment() {
    notifications.insertAll(
        List.of(
            draft(A, NotificationType.COMMENT_REPLY, 500L),
            draft(B, NotificationType.MENTION, 500L),
            draft(B, NotificationType.MENTION, 501L)));
    notifications.markCommentDeleted(501L);

    ExcerptText edited = ExcerptText.of("改过了 <@7>", true);
    assertThat(notifications.refreshExcerpt(500L, edited)).isEqualTo(2);
    assertThat(notifications.refreshExcerpt(501L, edited)).isZero();

    assertThat(
            jdbcTemplate.queryForList(
                "SELECT excerpt FROM notification.notification WHERE comment_id = 500 AND"
                    + " excerpt_truncated",
                String.class))
        .containsExactly("改过了 <@7>", "改过了 <@7>");
  }

  /// 评论删除后：标删除、摘录清空。
  @Test
  void should_clear_excerpt_when_comment_deleted() {
    notifications.insertAll(List.of(draft(A, NotificationType.COMMENT_REPLY, 500L)));

    assertThat(notifications.markCommentDeleted(500L)).isEqualTo(1);

    assertThat(
            jdbcTemplate.queryForMap(
                "SELECT excerpt, comment_deleted FROM notification.notification"
                    + " WHERE recipient_id = ?",
                A))
        .containsEntry("excerpt", null)
        .containsEntry("comment_deleted", true);
  }

  /// 已删除的评论不能留着摘录（约束兜底）。
  @Test
  void should_reject_excerpt_on_deleted_comment_by_constraint() {
    notifications.insertAll(List.of(draft(A, NotificationType.COMMENT_REPLY, 500L)));

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE notification.notification SET comment_deleted = true"
                        + " WHERE recipient_id = ?",
                    A))
        .hasMessageContaining("ck_notification_deleted_excerpt");
  }

  /// 只能标自己的；已读的再标不改已读时刻。
  @Test
  void should_mark_read_only_own_notification() {
    notifications.insertAll(List.of(draft(A, NotificationType.COMMENT_REPLY, 500L)));
    long id = idOf(A);

    assertThat(notifications.markRead(B, id, T0)).isFalse();
    assertThat(notifications.markRead(A, id, T0)).isTrue();
    assertThat(notifications.markRead(A, id, T0.plusSeconds(60))).isTrue();

    assertThat(
            jdbcTemplate
                .queryForObject(
                    "SELECT read_at FROM notification.notification WHERE recipient_id = ?",
                    Timestamp.class,
                    A)
                .toInstant())
        .isEqualTo(T0);
  }

  /// 全部已读只动收件人自己的。
  @Test
  void should_mark_all_read_for_recipient_only() {
    notifications.insertAll(
        List.of(
            draft(A, NotificationType.COMMENT_REPLY, 500L),
            draft(A, NotificationType.MENTION, 501L),
            draft(B, NotificationType.COMMENT_REPLY, 500L),
            draft(B, NotificationType.MENTION, 501L)));

    assertThat(notifications.markAllRead(A, T0)).isEqualTo(2);
    assertThat(notifications.markAllRead(A, T0)).isZero();

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notification.notification"
                    + " WHERE recipient_id = ? AND read_at IS NULL",
                Long.class,
                B))
        .isEqualTo(2L);
  }

  /// 只删截止时刻之前的，正好在截止时刻的留下。
  @Test
  void should_delete_only_rows_before_cutoff() {
    notifications.insertAll(
        List.of(
            draft(A, NotificationType.MENTION, 500L, T0.minus(1, ChronoUnit.MICROS)),
            draft(A, NotificationType.MENTION, 501L, T0)));

    assertThat(notifications.deleteCreatedBefore(T0)).isEqualTo(1);

    assertThat(
            jdbcTemplate.queryForList(
                "SELECT comment_id FROM notification.notification", Long.class))
        .containsExactly(501L);
  }

  /// 有提醒的收件人按 ID 游标列出；删掉一个人的全部提醒后他不再出现。
  @Test
  void should_delete_all_of_recipient_and_list_recipients_after_cursor() {
    notifications.insertAll(
        List.of(
            draft(5L, NotificationType.MENTION, 500L),
            draft(7L, NotificationType.MENTION, 500L),
            draft(7L, NotificationType.MENTION, 501L),
            draft(9L, NotificationType.MENTION, 500L)));

    assertThat(notifications.findRecipientsAfter(5L, 10)).containsExactly(7L, 9L);
    assertThat(notifications.deleteAllOf(7L)).isEqualTo(2);
    assertThat(notifications.findRecipientsAfter(5L, 10)).containsExactly(9L);
  }

  /// 空列表不查库。
  @Test
  void should_insert_nothing_for_empty_list() {
    assertThat(notifications.insertAll(List.of())).isZero();
  }

  /// 收件人唯一的一条提醒的 ID。
  ///
  /// @param recipient 收件人
  /// @return 提醒 ID
  private long idOf(long recipient) {
    return jdbcTemplate.queryForObject(
        "SELECT notification_id FROM notification.notification WHERE recipient_id = ?",
        Long.class,
        recipient);
  }
}
