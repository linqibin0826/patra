package dev.patra.notification.app.mail.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import dev.patra.notification.domain.inbox.event.NotificationsCreatedEvent;
import dev.patra.notification.domain.inbox.model.read.CreatedNotification;
import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import dev.patra.notification.domain.mail.model.vo.QueuedReminder;
import dev.patra.notification.domain.mail.port.repository.ReminderQueueRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/// 提醒写入信箱时排进离站邮件的队（spec 5.1、7.1）。
class ReminderQueueListenerTest {

  private static final Instant OCCURRED = Instant.parse("2026-10-30T06:00:00Z");
  private static final Instant CREATED = Instant.parse("2026-10-30T07:02:00Z");

  private final ReminderQueueRepository queue = mock(ReminderQueueRepository.class);
  private final ReminderQueueListener listener = new ReminderQueueListener(queue);

  /// 回复与 @ 入队，按写入信箱的时刻（不是评论时刻）算 10 分钟。
  @Test
  void queues_reply_and_mention_at_created_at() {
    listener.on(
        NotificationsCreatedEvent.of(
            List.of(
                created(1L, NotificationCategory.REPLY),
                created(2L, NotificationCategory.MENTION))));

    ArgumentCaptor<List<QueuedReminder>> rows = ArgumentCaptor.captor();
    verify(queue).enqueue(rows.capture());
    assertThat(rows.getValue())
        .containsExactly(
            QueuedReminder.queue(1L, 7L, NotificationCategory.REPLY, CREATED),
            QueuedReminder.queue(2L, 7L, NotificationCategory.MENTION, CREATED));
    assertThat(rows.getValue().getFirst().dueAt()).isEqualTo(CREATED.plus(Duration.ofMinutes(10)));
  }

  /// 有帮助没有邮件，不入队。
  @Test
  void ignores_helpful() {
    listener.on(
        NotificationsCreatedEvent.of(
            List.of(
                created(1L, NotificationCategory.HELPFUL),
                created(2L, NotificationCategory.REPLY))));

    ArgumentCaptor<List<QueuedReminder>> rows = ArgumentCaptor.captor();
    verify(queue).enqueue(rows.capture());
    assertThat(rows.getValue()).extracting(QueuedReminder::notificationId).containsExactly(2L);
  }

  /// 一条都不用排时不碰仓储。
  @Test
  void nothing_to_queue_skips_repository() {
    listener.on(NotificationsCreatedEvent.of(List.of(created(1L, NotificationCategory.HELPFUL))));

    verify(queue, never()).enqueue(any());
  }

  /// 一条刚写入信箱的提醒。
  ///
  /// @param id id
  /// @param category category
  /// @return 结果
  private static CreatedNotification created(long id, NotificationCategory category) {
    return CreatedNotification.builder()
        .notificationId(id)
        .recipientId(7L)
        .category(category)
        .occurredAt(OCCURRED)
        .createdAt(CREATED)
        .build();
  }
}
