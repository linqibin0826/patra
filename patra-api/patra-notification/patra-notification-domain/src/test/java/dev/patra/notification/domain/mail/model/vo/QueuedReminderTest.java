package dev.patra.notification.domain.mail.model.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/// 在等 10 分钟的一条提醒（spec 5.1）。
class QueuedReminderTest {

  private static final Instant CREATED = Instant.parse("2026-10-30T07:02:00Z");

  /// 到点时刻是写入信箱后 10 分钟。
  @Test
  void queue_sets_due_ten_minutes_after_created() {
    QueuedReminder row = QueuedReminder.queue(11L, 7L, NotificationCategory.REPLY, CREATED);

    assertThat(row.notificationId()).isEqualTo(11L);
    assertThat(row.recipientId()).isEqualTo(7L);
    assertThat(row.category()).isEqualTo(NotificationCategory.REPLY);
    assertThat(row.queuedAt()).isEqualTo(CREATED);
    assertThat(row.dueAt()).isEqualTo(CREATED.plus(Duration.ofMinutes(10)));
  }

  /// 有帮助没有邮件，不能排队。
  @Test
  void queue_rejects_helpful() {
    assertThatThrownBy(() -> QueuedReminder.queue(11L, 7L, NotificationCategory.HELPFUL, CREATED))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
