package dev.patra.notification.infra.inbox.persistence;

import static dev.patra.notification.domain.inbox.model.enums.NotificationType.COMMENT_REPLY;
import static dev.patra.notification.domain.inbox.model.enums.NotificationType.MENTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.patra.notification.domain.inbox.model.enums.MarkReadOutcome;
import dev.patra.notification.domain.inbox.model.read.CreatedNotification;
import dev.patra.notification.domain.inbox.model.vo.NotificationDraft;
import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/// 提醒的写端：唯一索引挡重复、按评论 / 对象删除、已读、保留期与注销清理。
class NotificationRepositoryAdapterIT extends InboxPersistenceITSupport {

  @Autowired private NotificationRepositoryAdapter notifications;

  /// 同一人同一评论已有回复提醒时，再写 @ 提醒被跳过（唯一索引兜底）。
  @Test
  void should_skip_second_reply_or_mention_for_same_comment() {
    notifications.insertAll(List.of(draft(2L, COMMENT_REPLY, 500L)));

    List<CreatedNotification> second = notifications.insertAll(List.of(draft(2L, MENTION, 500L)));

    assertThat(second).isEmpty();
    assertThat(notificationRows()).isEqualTo(1);
  }

  /// 返回真写入的行：带雪花 ID、类别、两个时刻。
  @Test
  void should_return_inserted_rows() {
    NotificationDraft late = draft(4L, MENTION, 500L, Instant.parse("2026-10-30T23:00:00Z"));

    List<CreatedNotification> created =
        notifications.insertAll(List.of(draft(2L, COMMENT_REPLY, 500L), late));

    assertThat(created)
        .extracting(CreatedNotification::recipientId, CreatedNotification::category)
        .containsExactlyInAnyOrder(
            tuple(2L, NotificationCategory.REPLY), tuple(4L, NotificationCategory.MENTION));
    assertThat(created).allSatisfy(c -> assertThat(c.notificationId()).isPositive());
    assertThat(created)
        .filteredOn(c -> c.recipientId() == 4L)
        .singleElement()
        .satisfies(
            c -> {
              assertThat(c.occurredAt()).isEqualTo(Instant.parse("2026-10-30T23:00:00Z"));
              assertThat(c.createdAt()).isEqualTo(NOW);
            });
  }

  /// 评论删除：删掉所有收件人的提醒，返回受影响的收件人。
  @Test
  void should_delete_by_comment_for_all_recipients() {
    notifications.insertAll(
        List.of(
            draft(2L, COMMENT_REPLY, 500L), draft(4L, MENTION, 500L), draft(2L, MENTION, 501L)));

    assertThat(notifications.deleteByComment(500L)).containsExactlyInAnyOrder(2L, 4L);
    assertThat(notificationRows()).isEqualTo(1);
  }

  /// 经验删除：删掉这个对象下的全部提醒。
  @Test
  void should_delete_by_subject() {
    notifications.insertAll(List.of(draft(2L, COMMENT_REPLY, 500L), draft(4L, MENTION, 501L)));
    jdbcTemplate.update("UPDATE inbox.notification SET subject_id = 41 WHERE recipient_id = 4");

    assertThat(notifications.deleteBySubject(40L)).containsExactly(2L);
    assertThat(notificationRows()).isEqualTo(1);
  }

  /// 单条已读：第一次 MARKED，再标 ALREADY_READ 且时刻不变；别人的 NOT_FOUND。
  @Test
  void should_mark_read_once() {
    long id =
        notifications
            .insertAll(List.of(draft(2L, COMMENT_REPLY, 500L)))
            .getFirst()
            .notificationId();

    assertThat(notifications.markRead(2L, id, NOW)).isEqualTo(MarkReadOutcome.MARKED);
    assertThat(notifications.markRead(2L, id, NOW.plusSeconds(60)))
        .isEqualTo(MarkReadOutcome.ALREADY_READ);
    assertThat(notifications.markRead(3L, id, NOW)).isEqualTo(MarkReadOutcome.NOT_FOUND);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT read_at = ?::timestamptz FROM inbox.notification",
                Boolean.class,
                NOW.toString()))
        .isTrue();
  }

  /// 全部已读：跨类别；没有未读时为 0。
  @Test
  void should_mark_all_read_across_categories() {
    notifications.insertAll(List.of(draft(2L, COMMENT_REPLY, 500L), draft(2L, MENTION, 501L)));

    assertThat(notifications.markAllRead(2L, NOW)).isEqualTo(2);
    assertThat(notifications.markAllRead(2L, NOW)).isZero();
  }

  /// 保留期：只删排序时间早于截止的。
  @Test
  void should_delete_occurred_before() {
    notifications.insertAll(
        List.of(
            draft(2L, COMMENT_REPLY, 500L, Instant.parse("2026-05-01T00:00:00Z")),
            draft(2L, COMMENT_REPLY, 501L, Instant.parse("2026-06-01T00:00:00Z"))));

    assertThat(notifications.deleteOccurredBefore(Instant.parse("2026-05-31T00:00:00Z")))
        .isEqualTo(1);
    assertThat(notificationRows()).isEqualTo(1);
  }

  /// 注销清理：按用户 ID 升序分批列出收件人，删一个人的全部提醒。
  @Test
  void should_list_recipients_and_delete_all_of_one() {
    notifications.insertAll(
        List.of(
            draft(7L, COMMENT_REPLY, 500L),
            draft(2L, COMMENT_REPLY, 501L),
            draft(4L, MENTION, 500L),
            draft(4L, MENTION, 502L)));

    assertThat(notifications.findRecipientsAfter(2L, 10)).containsExactly(4L, 7L);
    assertThat(notifications.findRecipientsAfter(0L, 1)).containsExactly(2L);
    assertThat(notifications.deleteAllOf(4L)).isEqualTo(2);
    assertThat(notificationRows()).isEqualTo(2);
  }
}
