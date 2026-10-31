package dev.patra.notification.domain.mail.model.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import dev.patra.notification.domain.mail.model.enums.DeliveryKind;
import dev.patra.notification.domain.mail.model.enums.DeliveryStatus;
import dev.patra.notification.domain.mail.model.enums.SkipReason;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/// 一封提醒邮件的投递记录：退避、第一次发送时刻、24 小时过期（spec 2.4、2.5、5.4）。
class ReminderDeliveryTest {

  private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000146");
  private static final Instant NOW = Instant.parse("2026-10-30T07:12:00Z");

  /// 新建的逐条投递马上可以尝试。
  @Test
  void instant_starts_pending_and_due_now() {
    ReminderDelivery delivery = instant();

    assertThat(delivery.getDeliveryId()).isEqualTo(ID);
    assertThat(delivery.getKind()).isEqualTo(DeliveryKind.INSTANT);
    assertThat(delivery.getCategory()).isEqualTo(NotificationCategory.MENTION);
    assertThat(delivery.getNotificationIds()).containsExactly(11L, 12L);
    assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PENDING);
    assertThat(delivery.getAttempts()).isZero();
    assertThat(delivery.getNextAttemptAt()).isEqualTo(NOW);
    assertThat(delivery.getCreatedAt()).isEqualTo(NOW);
    assertThat(delivery.getFirstSentAt()).isNull();
  }

  /// 汇总投递带窗口右端，不带类别与提醒 ID。
  @Test
  void digest_carries_window_end() {
    Instant windowEnd = Instant.parse("2026-10-31T00:00:00Z");

    ReminderDelivery delivery = ReminderDelivery.digest(ID, 7L, windowEnd, NOW);

    assertThat(delivery.getKind()).isEqualTo(DeliveryKind.DIGEST);
    assertThat(delivery.getWindowEnd()).isEqualTo(windowEnd);
    assertThat(delivery.getCategory()).isNull();
    assertThat(delivery.getNotificationIds()).isEmpty();
  }

  /// 逐条投递必须有类别、至少一条提醒，类别要有邮件。
  @Test
  void instant_requires_category_and_ids() {
    assertThatThrownBy(
            () -> ReminderDelivery.instant(ID, 7L, NotificationCategory.REPLY, List.of(), NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> ReminderDelivery.instant(ID, 7L, NotificationCategory.HELPFUL, List.of(1L), NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /// 退避 1、2、4、8、16 分钟，之后都是 30 分钟。
  @Test
  void backoff_doubles_and_caps_at_thirty_minutes() {
    assertThat(List.of(1, 2, 3, 4, 5, 6, 7, 8).stream().map(ReminderDelivery::backoff))
        .containsExactly(
            Duration.ofMinutes(1),
            Duration.ofMinutes(2),
            Duration.ofMinutes(4),
            Duration.ofMinutes(8),
            Duration.ofMinutes(16),
            Duration.ofMinutes(30),
            Duration.ofMinutes(30),
            Duration.ofMinutes(30));
    assertThatThrownBy(() -> ReminderDelivery.backoff(0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /// 推迟一次记一次失败，下一次尝试按退避往后挪。
  @Test
  void defer_counts_attempt_and_moves_next_attempt() {
    ReminderDelivery delivery = instant();

    delivery.defer(NOW);
    Instant later = NOW.plus(Duration.ofMinutes(1));
    delivery.defer(later);

    assertThat(delivery.getAttempts()).isEqualTo(2);
    assertThat(delivery.getNextAttemptAt()).isEqualTo(later.plus(Duration.ofMinutes(2)));
    assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PENDING);
  }

  /// 第一次发送时刻只记一次。
  @Test
  void first_send_attempt_is_recorded_once() {
    ReminderDelivery delivery = instant();

    delivery.recordSendAttempt(NOW);
    delivery.recordSendAttempt(NOW.plus(Duration.ofHours(1)));

    assertThat(delivery.getFirstSentAt()).isEqualTo(NOW);
  }

  /// 还没调用过发信端口，推迟多久都不过期。
  @Test
  void never_sent_defers_forever() {
    ReminderDelivery delivery = instant();

    delivery.defer(NOW.plus(Duration.ofDays(30)));

    assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PENDING);
    assertThat(delivery.getFinishedAt()).isNull();
  }

  /// 调用过发信端口，满 24 小时后再推迟就过期。
  @Test
  void defer_expires_24_hours_after_first_send() {
    ReminderDelivery delivery = instant();
    delivery.recordSendAttempt(NOW);

    delivery.defer(NOW.plus(Duration.ofHours(24)).minusSeconds(60));
    assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PENDING);

    Instant expiry = NOW.plus(ReminderDelivery.SEND_WINDOW);
    delivery.defer(expiry);
    assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.EXPIRED);
    assertThat(delivery.getFinishedAt()).isEqualTo(expiry);
  }

  /// 跳过记下原因与结束时刻。
  @Test
  void skip_records_reason_and_finish() {
    ReminderDelivery delivery = instant();

    delivery.skip(SkipReason.RECIPIENT, NOW);

    assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.SKIPPED);
    assertThat(delivery.getSkipReason()).isEqualTo(SkipReason.RECIPIENT);
    assertThat(delivery.getFinishedAt()).isEqualTo(NOW);
  }

  /// 结束了的投递不能再改。
  @Test
  void finished_delivery_cannot_change() {
    ReminderDelivery delivery = instant();
    delivery.markSent(NOW);

    assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.SENT);
    assertThat(delivery.getFinishedAt()).isEqualTo(NOW);
    assertThatThrownBy(() -> delivery.defer(NOW)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> delivery.skip(SkipReason.MODE, NOW))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> delivery.markSent(NOW)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> delivery.recordSendAttempt(NOW))
        .isInstanceOf(IllegalStateException.class);
  }

  private static ReminderDelivery instant() {
    return ReminderDelivery.instant(ID, 7L, NotificationCategory.MENTION, List.of(11L, 12L), NOW);
  }
}
