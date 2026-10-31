package dev.patra.notification.app.mail.usecase.flush;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.notification.domain.inbox.model.read.InboxEntry;
import dev.patra.notification.domain.inbox.model.read.NotificationType;
import dev.patra.notification.domain.inbox.port.read.InboxReadPort;
import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import dev.patra.notification.domain.kernel.port.gateway.PresenceGateway;
import dev.patra.notification.domain.mail.model.aggregate.ReminderDelivery;
import dev.patra.notification.domain.mail.model.enums.DeliveryKind;
import dev.patra.notification.domain.mail.model.vo.QueuedReminder;
import dev.patra.notification.domain.mail.port.repository.ReminderDeliveryRepository;
import dev.patra.notification.domain.mail.port.repository.ReminderQueueRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionOperations;

/// 每分钟把到点的排队行处理掉：丢弃已读与看过的，到点的一类合成一条投递（spec 5.1、7.2）。
class FlushDueRemindersHandlerTest {

  private static final Instant T0 = Instant.parse("2026-10-30T07:02:00Z");
  private static final Instant NOW = T0.plus(Duration.ofMinutes(10));

  private final ReminderQueueRepository queue = mock(ReminderQueueRepository.class);
  private final ReminderDeliveryRepository deliveries = mock(ReminderDeliveryRepository.class);
  private final InboxReadPort inbox = mock(InboxReadPort.class);
  private final PresenceGateway presence = mock(PresenceGateway.class);
  private final FlushDueRemindersHandler handler =
      new FlushDueRemindersHandler(
          queue,
          deliveries,
          inbox,
          presence,
          TransactionOperations.withoutTransaction(),
          Clock.fixed(NOW, ZoneOffset.UTC));

  /// 到点的一类合成一条逐条投递，排队行删掉。
  @Test
  void due_batch_becomes_one_delivery_and_rows_are_deleted() {
    dueRecipients(List.of(7L));
    when(queue.findByRecipient(7L))
        .thenReturn(List.of(reply(1L, T0), reply(2L, T0.plus(Duration.ofMinutes(4)))));
    unread(7L, 1L, 2L);

    FlushResult result = handler.handle(FlushDueRemindersCommand.of());

    ArgumentCaptor<ReminderDelivery> delivery = ArgumentCaptor.captor();
    verify(deliveries).insert(delivery.capture());
    assertThat(delivery.getValue().getKind()).isEqualTo(DeliveryKind.INSTANT);
    assertThat(delivery.getValue().getRecipientId()).isEqualTo(7L);
    assertThat(delivery.getValue().getCategory()).isEqualTo(NotificationCategory.REPLY);
    assertThat(delivery.getValue().getNotificationIds()).containsExactly(1L, 2L);
    assertThat(delivery.getValue().getNextAttemptAt()).isEqualTo(NOW);
    verify(queue).delete(List.of(1L, 2L));
    assertThat(result).isEqualTo(FlushResult.of(1, 0, 1));
  }

  /// 已读的与出现之后上过站的丢弃，不建投递。
  @Test
  void dropped_rows_are_deleted_without_delivery() {
    dueRecipients(List.of(7L));
    when(queue.findByRecipient(7L)).thenReturn(List.of(reply(1L, T0), reply(2L, T0)));
    unread(7L, 2L);
    when(presence.lastSeen(List.of(7L))).thenReturn(Map.of(7L, T0.plusSeconds(30)));

    FlushResult result = handler.handle(FlushDueRemindersCommand.of());

    verify(deliveries, never()).insert(any());
    verify(queue).delete(List.of(1L, 2L));
    assertThat(result).isEqualTo(FlushResult.of(1, 2, 0));
  }

  /// 没到点的那一类原样留着。
  @Test
  void not_due_category_is_left_alone() {
    dueRecipients(List.of(7L));
    when(queue.findByRecipient(7L))
        .thenReturn(
            List.of(
                reply(1L, T0),
                QueuedReminder.queue(
                    2L, 7L, NotificationCategory.MENTION, T0.plus(Duration.ofMinutes(5)))));
    unread(7L, 1L, 2L);

    handler.handle(FlushDueRemindersCommand.of());

    verify(queue).delete(List.of(1L));
  }

  /// 收件人按页取：一页满了，以最后一个 ID 为游标再取。
  @Test
  void pages_through_recipients() {
    List<Long> firstPage = LongStream.rangeClosed(1, 100).boxed().toList();
    when(queue.recipientsDue(NOW, 0L, 100)).thenReturn(firstPage);
    when(queue.recipientsDue(NOW, 100L, 100)).thenReturn(List.of(101L));
    when(queue.recipientsDue(NOW, 101L, 100)).thenReturn(List.of());
    when(queue.findByRecipient(anyLong())).thenReturn(List.of());

    FlushResult result = handler.handle(FlushDueRemindersCommand.of());

    assertThat(result.recipients()).isEqualTo(101);
    verify(queue).recipientsDue(NOW, 101L, 100);
  }

  /// 一个人出错（这一人的事务回滚）不影响后面的人。
  @Test
  void one_recipient_failing_does_not_stop_others() {
    dueRecipients(List.of(7L, 8L));
    when(queue.findByRecipient(7L)).thenThrow(new IllegalStateException("坏数据"));
    when(queue.findByRecipient(8L))
        .thenReturn(List.of(QueuedReminder.queue(3L, 8L, NotificationCategory.REPLY, T0)));
    unread(8L, 3L);

    FlushResult result = handler.handle(FlushDueRemindersCommand.of());

    verify(deliveries).insert(any());
    assertThat(result).isEqualTo(FlushResult.of(1, 0, 1));
  }

  private void dueRecipients(List<Long> recipients) {
    when(queue.recipientsDue(NOW, 0L, 100)).thenReturn(recipients);
    when(queue.recipientsDue(eq(NOW), eq(recipients.getLast()), eq(100))).thenReturn(List.of());
  }

  private void unread(long recipientId, Long... ids) {
    when(inbox.findUnread(eq(recipientId), anyCollection()))
        .thenReturn(List.of(ids).stream().map(FlushDueRemindersHandlerTest::entry).toList());
  }

  private static QueuedReminder reply(long id, Instant queuedAt) {
    return QueuedReminder.queue(id, 7L, NotificationCategory.REPLY, queuedAt);
  }

  private static InboxEntry entry(long id) {
    return InboxEntry.builder()
        .notificationId(id)
        .type(NotificationType.COMMENT_REPLY)
        .actorId(9L)
        .subjectId(40L)
        .commentId(500L + id)
        .plainText("好")
        .occurredAt(T0)
        .createdAt(T0)
        .build();
  }
}
