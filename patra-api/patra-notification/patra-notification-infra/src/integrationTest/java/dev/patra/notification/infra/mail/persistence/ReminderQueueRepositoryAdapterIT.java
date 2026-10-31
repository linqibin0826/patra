package dev.patra.notification.infra.mail.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import dev.patra.notification.domain.mail.model.vo.QueuedReminder;
import dev.patra.notification.domain.mail.port.repository.ReminderQueueRepository;
import dev.patra.notification.infra.config.NotificationITPostgreSQLContainerInitializer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/// 离站逐条的排队表：入队幂等、按到点找收件人、取一个人的全部行、删除（spec 6.1）。
@SpringBootTest
@ContextConfiguration(initializers = NotificationITPostgreSQLContainerInitializer.class)
@ActiveProfiles("test")
@DisplayName("提醒邮件排队表的仓储")
class ReminderQueueRepositoryAdapterIT {

  private static final Instant T0 = Instant.parse("2026-10-30T07:02:00.123456Z");

  @Autowired private ReminderQueueRepository queue;
  @Autowired private TransactionTemplate transactions;
  @Autowired private JdbcClient jdbcClient;

  @BeforeEach
  void clean() {
    jdbcClient.sql("TRUNCATE mail.reminder_queue").update();
  }

  /// 同一条提醒入队两次只有一行。
  @Test
  void enqueue_is_idempotent_per_notification() {
    QueuedReminder row = QueuedReminder.queue(11L, 7L, NotificationCategory.REPLY, T0);

    transactions.executeWithoutResult(status -> queue.enqueue(List.of(row)));
    transactions.executeWithoutResult(status -> queue.enqueue(List.of(row)));

    assertThat(queue.findByRecipient(7L)).containsExactly(row);
  }

  /// 入队只能在调用方事务里（信箱写入的事务）。
  @Test
  void enqueue_outside_transaction_fails() {
    assertThatThrownBy(
            () ->
                queue.enqueue(
                    List.of(QueuedReminder.queue(11L, 7L, NotificationCategory.REPLY, T0))))
        .isInstanceOf(IllegalTransactionStateException.class);
  }

  /// 有到点行的收件人按 ID 升序、去重；游标之后才取。
  @Test
  void recipients_due_pages_in_ascending_order() {
    enqueue(
        QueuedReminder.queue(1L, 9L, NotificationCategory.REPLY, T0),
        QueuedReminder.queue(2L, 9L, NotificationCategory.MENTION, T0),
        QueuedReminder.queue(3L, 5L, NotificationCategory.REPLY, T0),
        QueuedReminder.queue(4L, 6L, NotificationCategory.REPLY, T0.plus(Duration.ofMinutes(5))));
    Instant now = T0.plus(Duration.ofMinutes(10));

    assertThat(queue.recipientsDue(now, 0L, 10)).containsExactly(5L, 9L);
    assertThat(queue.recipientsDue(now, 5L, 10)).containsExactly(9L);
    assertThat(queue.recipientsDue(now, 0L, 1)).containsExactly(5L);
  }

  /// 取一个人的全部行：两类都在、没到点的也在。
  @Test
  void find_by_recipient_returns_all_categories_including_not_due() {
    QueuedReminder reply = QueuedReminder.queue(1L, 7L, NotificationCategory.REPLY, T0);
    QueuedReminder mention =
        QueuedReminder.queue(2L, 7L, NotificationCategory.MENTION, T0.plus(Duration.ofMinutes(9)));
    enqueue(reply, mention, QueuedReminder.queue(3L, 8L, NotificationCategory.REPLY, T0));

    assertThat(queue.findByRecipient(7L)).containsExactlyInAnyOrder(reply, mention);
  }

  /// 认领：删掉给定的行，只返回真删到的 ID（不在队里的、已被别人认领的不返回）。
  @Test
  void claim_returns_only_rows_it_deleted() {
    enqueue(
        QueuedReminder.queue(1L, 7L, NotificationCategory.REPLY, T0),
        QueuedReminder.queue(2L, 7L, NotificationCategory.REPLY, T0));

    assertThat(queue.claim(List.of(1L, 99L))).containsExactly(1L);
    assertThat(queue.claim(List.of(1L))).isEmpty();
    assertThat(queue.claim(List.of())).isEmpty();
    assertThat(queue.findByRecipient(7L))
        .extracting(QueuedReminder::notificationId)
        .containsExactly(2L);
  }

  private void enqueue(QueuedReminder... rows) {
    transactions.executeWithoutResult(status -> queue.enqueue(List.of(rows)));
  }
}
