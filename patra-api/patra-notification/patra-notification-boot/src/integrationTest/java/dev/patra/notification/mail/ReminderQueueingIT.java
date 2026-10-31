package dev.patra.notification.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.notification.app.inbox.service.NotificationWriter;
import dev.patra.notification.domain.inbox.model.read.NotificationType;
import dev.patra.notification.domain.inbox.model.vo.NotificationDraft;
import dev.patra.notification.support.NotificationFlowITSupport;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/// 信箱写入提醒时，离站邮件的排队行在同一个事务里写进 `mail.reminder_queue`（spec 2.2、7.1）。
class ReminderQueueingIT extends NotificationFlowITSupport {

  private static final Instant CREATED = Instant.parse("2026-10-30T07:02:00Z");

  @Autowired private NotificationWriter writer;
  @Autowired private TransactionTemplate transactions;

  /// 写入一条回复提醒：排队行在，到点是写入时刻加 10 分钟。
  @Test
  void reply_notification_is_queued_in_same_transaction() {
    transactions.executeWithoutResult(
        status -> writer.write(List.of(draft(NotificationType.COMMENT_REPLY, 500L))));

    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "SELECT recipient_id, category, queued_at, due_at FROM mail.reminder_queue");
    assertThat(row).containsEntry("recipient_id", 7L).containsEntry("category", "REPLY");
    assertThat(((Timestamp) row.get("queued_at")).toInstant()).isEqualTo(CREATED);
    assertThat(((Timestamp) row.get("due_at")).toInstant())
        .isEqualTo(CREATED.plus(Duration.ofMinutes(10)));
  }

  /// 信箱事务回滚，排队行也没有。
  @Test
  void inbox_rollback_rolls_back_queue() {
    assertThatThrownBy(
            () ->
                transactions.executeWithoutResult(
                    status -> {
                      writer.write(List.of(draft(NotificationType.MENTION, 501L)));
                      throw new IllegalStateException("后续步骤失败");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM inbox.notification", Long.class))
        .isZero();
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM mail.reminder_queue", Long.class))
        .isZero();
  }

  private static NotificationDraft draft(NotificationType type, long commentId) {
    return NotificationDraft.builder()
        .recipientId(7L)
        .type(type)
        .actorId(9L)
        .actorAnonymous(false)
        .subjectId(40L)
        .commentId(commentId)
        .occurredAt(CREATED.minusSeconds(5))
        .createdAt(CREATED)
        .build();
  }
}
