package dev.patra.notification.infra.mail.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import dev.patra.notification.domain.mail.model.aggregate.ReminderDelivery;
import dev.patra.notification.domain.mail.model.enums.DeliveryStatus;
import dev.patra.notification.domain.mail.model.enums.SkipReason;
import dev.patra.notification.domain.mail.port.repository.ReminderDeliveryRepository;
import dev.patra.notification.infra.config.NotificationITPostgreSQLContainerInitializer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// 提醒邮件的投递记录：读写往返、汇总一个窗口一条、按到点取、清理、约束（spec 6.2）。
@SpringBootTest
@ContextConfiguration(initializers = NotificationITPostgreSQLContainerInitializer.class)
@ActiveProfiles("test")
@DisplayName("提醒邮件投递记录的仓储")
class ReminderDeliveryRepositoryAdapterIT {

  private static final Instant NOW = Instant.parse("2026-10-30T07:12:00.654321Z");
  private static final Instant WINDOW_END = Instant.parse("2026-10-31T00:00:00Z");

  @Autowired private ReminderDeliveryRepository deliveries;
  @Autowired private JdbcClient jdbcClient;

  /// 每例前清空投递表。
  @BeforeEach
  void clean() {
    jdbcClient.sql("TRUNCATE mail.reminder_delivery").update();
  }

  /// 逐条投递写进去、按到点读回，字段与提醒 ID 的顺序都在。
  @Test
  void instant_round_trips() {
    ReminderDelivery delivery = instant(UUID.randomUUID(), 7L, List.of(12L, 11L));

    deliveries.insert(delivery);

    assertThat(deliveries.findDue(NOW, 10))
        .singleElement()
        .usingRecursiveComparison()
        .isEqualTo(delivery);
  }

  /// 同一个人同一个窗口的汇总只插一次；换个窗口照常。
  @Test
  void digest_insert_is_unique_per_window() {
    assertThat(
            deliveries.insertDigestIfAbsent(
                ReminderDelivery.digest(UUID.randomUUID(), 7L, WINDOW_END, NOW)))
        .isTrue();
    assertThat(
            deliveries.insertDigestIfAbsent(
                ReminderDelivery.digest(UUID.randomUUID(), 7L, WINDOW_END, NOW)))
        .isFalse();
    assertThat(
            deliveries.insertDigestIfAbsent(
                ReminderDelivery.digest(
                    UUID.randomUUID(), 7L, WINDOW_END.plus(Duration.ofDays(1)), NOW)))
        .isTrue();
    assertThat(
            deliveries.insertDigestIfAbsent(
                ReminderDelivery.digest(UUID.randomUUID(), 8L, WINDOW_END, NOW)))
        .isTrue();
  }

  /// 只取待发且已到下一次尝试时刻的，按时刻先后。
  @Test
  void find_due_skips_future_and_finished() {
    ReminderDelivery later = instant(UUID.randomUUID(), 7L, List.of(1L));
    later.defer(NOW);
    ReminderDelivery finished = instant(UUID.randomUUID(), 8L, List.of(2L));
    finished.markSent(NOW);
    ReminderDelivery due = instant(UUID.randomUUID(), 9L, List.of(3L));
    deliveries.insert(later);
    deliveries.insert(finished);
    deliveries.insert(due);

    assertThat(deliveries.findDue(NOW, 10))
        .extracting(ReminderDelivery::getDeliveryId)
        .containsExactly(due.getDeliveryId());
    assertThat(deliveries.findDue(NOW.plus(Duration.ofMinutes(1)), 10))
        .extracting(ReminderDelivery::getDeliveryId)
        .containsExactly(due.getDeliveryId(), later.getDeliveryId());
  }

  /// 状态、失败次数、下一次尝试、第一次发送、结束时刻都写回去。
  @Test
  void update_persists_attempts_and_finish() {
    ReminderDelivery delivery = instant(UUID.randomUUID(), 7L, List.of(1L));
    deliveries.insert(delivery);

    delivery.recordSendAttempt(NOW);
    delivery.defer(NOW);
    deliveries.update(delivery);
    ReminderDelivery stored = deliveries.findDue(NOW.plus(Duration.ofMinutes(1)), 10).getFirst();
    assertThat(stored).usingRecursiveComparison().isEqualTo(delivery);

    stored.skip(SkipReason.REJECTED, NOW.plus(Duration.ofMinutes(1)));
    deliveries.update(stored);
    assertThat(deliveries.findDue(NOW.plus(Duration.ofDays(1)), 10)).isEmpty();
    assertThat(
            jdbcClient
                .sql("SELECT status, skip_reason FROM mail.reminder_delivery WHERE delivery_id = ?")
                .param(stored.getDeliveryId())
                .query()
                .singleRow())
        .containsEntry("status", DeliveryStatus.SKIPPED.name())
        .containsEntry("skip_reason", SkipReason.REJECTED.name());
  }

  /// 清理只删结束得早于截止时刻的，不碰待发的与刚结束的。
  @Test
  void delete_finished_before_keeps_pending_and_recent() {
    ReminderDelivery old = instant(UUID.randomUUID(), 7L, List.of(1L));
    old.markSent(NOW);
    ReminderDelivery recent = instant(UUID.randomUUID(), 8L, List.of(2L));
    recent.markSent(NOW.plus(Duration.ofDays(6)));
    ReminderDelivery pending = instant(UUID.randomUUID(), 9L, List.of(3L));
    deliveries.insert(old);
    deliveries.insert(recent);
    deliveries.insert(pending);

    assertThat(deliveries.deleteFinishedBefore(NOW.plus(Duration.ofDays(1)))).isEqualTo(1);
    assertThat(
            jdbcClient
                .sql("SELECT count(*) FROM mail.reminder_delivery")
                .query(Long.class)
                .single())
        .isEqualTo(2L);
  }

  /// 逐条投递带汇总窗口违反约束。
  @Test
  void check_constraints_reject_mixed_kinds() {
    assertThatThrownBy(
            () ->
                jdbcClient
                    .sql(
                        """
                        INSERT INTO mail.reminder_delivery
                          (delivery_id, recipient_id, kind, category, notification_ids, window_end,
                           status, attempts, next_attempt_at, created_at)
                        VALUES (?, 7, 'INSTANT', 'REPLY', ARRAY[1]::bigint[], now(), 'PENDING', 0, now(), now())
                        """)
                    .param(UUID.randomUUID())
                    .update())
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_reminder_delivery_kind");
  }

  /// 一条逐条投递。
  ///
  /// @param id id
  /// @param recipientId recipientId
  /// @param notificationIds notificationIds
  /// @return 结果
  private static ReminderDelivery instant(UUID id, long recipientId, List<Long> notificationIds) {
    return ReminderDelivery.instant(
        id, recipientId, NotificationCategory.REPLY, notificationIds, NOW);
  }
}
