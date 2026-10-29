package dev.patra.interaction.infra.notification.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.interaction.domain.notification.model.enums.NotificationType;
import dev.patra.interaction.domain.notification.model.vo.ExcerptText;
import dev.patra.interaction.domain.notification.model.vo.NotificationDraft;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
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

  /// 编辑后刷新这条评论的全部提醒，别的评论不动。
  @Test
  void should_refresh_excerpt_for_all_recipients_of_comment() {
    notifications.insertAll(
        List.of(
            draft(A, NotificationType.COMMENT_REPLY, 500L),
            draft(B, NotificationType.MENTION, 500L),
            draft(B, NotificationType.MENTION, 501L)));

    ExcerptText edited = ExcerptText.of("改过了 <@7>", true);
    assertThat(notifications.refreshExcerpt(500L, edited)).isEqualTo(2);

    assertThat(
            jdbcTemplate.queryForList(
                "SELECT excerpt FROM notification.notification WHERE comment_id = 500 AND"
                    + " excerpt_truncated",
                String.class))
        .containsExactly("改过了 <@7>", "改过了 <@7>");
  }

  /// 评论删除后：它的提醒跨收件人全部删掉，别的评论的提醒不动。
  @Test
  void should_delete_all_recipients_notifications_of_comment() {
    notifications.insertAll(
        List.of(
            draft(A, NotificationType.COMMENT_REPLY, 500L),
            draft(B, NotificationType.MENTION, 500L),
            draft(A, NotificationType.MENTION, 501L)));

    assertThat(notifications.deleteByComment(500L)).isEqualTo(2);

    assertThat(
            jdbcTemplate.queryForList(
                "SELECT comment_id FROM notification.notification", Long.class))
        .containsExactly(501L);
  }

  /// 经验删除后：这个对象下的提醒跨收件人全部删掉，别的对象不动。
  @Test
  void should_delete_all_notifications_of_subject() {
    notifications.insertAll(
        List.of(
            onSubject(draft(A, NotificationType.POST_COMMENT, 500L), 77L),
            onSubject(draft(B, NotificationType.COMMENT_REPLY, 501L), 77L),
            onSubject(draft(A, NotificationType.COMMENT_REPLY, 502L), 78L)));

    assertThat(notifications.deleteBySubject(SubjectId.of(77L))).isEqualTo(2);

    assertThat(
            jdbcTemplate.queryForList(
                "SELECT subject_id FROM notification.notification", Long.class))
        .containsExactly(78L);
  }

  /// 对象下没有提醒：删 0 条，不报错。
  @Test
  void should_return_zero_when_subject_has_no_notifications() {
    notifications.insertAll(List.of(draft(A, NotificationType.COMMENT_REPLY, 500L)));

    assertThat(notifications.deleteBySubject(SubjectId.of(77L))).isZero();
    assertThat(countOf(A)).isEqualTo(1L);
  }

  /// 同一条提醒，换到另一个对象下。
  ///
  /// @param draft 提醒
  /// @param subjectId 对象
  /// @return 换过对象的提醒
  private static NotificationDraft onSubject(NotificationDraft draft, long subjectId) {
    return NotificationDraft.builder()
        .recipientId(draft.recipientId())
        .type(draft.type())
        .actorId(draft.actorId())
        .actorAnonymous(draft.actorAnonymous())
        .subjectId(SubjectId.of(subjectId))
        .commentId(draft.commentId())
        .excerpt(draft.excerpt())
        .createdAt(draft.createdAt())
        .build();
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
