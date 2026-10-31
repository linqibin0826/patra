package dev.patra.notification.app.inbox.usecase.ingest;

import static dev.patra.notification.app.inbox.usecase.ingest.RecordCommentPublishedHandlerTest.EVENT;
import static dev.patra.notification.app.inbox.usecase.ingest.RecordCommentPublishedHandlerTest.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.notification.domain.inbox.event.UnreadCountsChangedEvent;
import dev.patra.notification.domain.inbox.port.repository.CommentContentRepository;
import dev.patra.notification.domain.inbox.port.repository.NotificationRepository;
import dev.patra.tool.core.event.ProcessedEventPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionOperations;

/// 评论已删除：先留墓碑（锁住快照行），再删它的全部提醒。
class RecordCommentDeletedHandlerTest {

  private static final Instant DELETED_AT = Instant.parse("2026-10-31T07:59:50Z");

  private final ProcessedEventPort processed = mock(ProcessedEventPort.class);
  private final CommentContentRepository comments = mock(CommentContentRepository.class);
  private final NotificationRepository notifications = mock(NotificationRepository.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final RecordCommentDeletedHandler handler =
      new RecordCommentDeletedHandler(
          processed,
          comments,
          notifications,
          events,
          TransactionOperations.withoutTransaction(),
          Clock.fixed(NOW, ZoneOffset.UTC));

  /// 留墓碑、删提醒，被删了提醒的人未读数变了。
  @Test
  void should_tombstone_and_delete_notifications() {
    when(processed.markProcessed(InboxConsumer.NAME, EVENT)).thenReturn(true);
    when(notifications.deleteByComment(500L)).thenReturn(Set.of(2L));

    assertThat(handler.handle(RecordCommentDeletedCommand.of(EVENT, 500L, 40L, DELETED_AT)))
        .isEqualTo(IngestOutcome.APPLIED);

    InOrder order = inOrder(comments, notifications);
    order.verify(comments).markDeleted(500L, 40L, DELETED_AT, NOW);
    order.verify(notifications).deleteByComment(500L);
    verify(events).publishEvent(UnreadCountsChangedEvent.of(Set.of(2L)));
  }

  /// 没有提醒被删（删除先于发表到达）：只留墓碑，不发事件。
  @Test
  void should_not_publish_when_none_deleted() {
    when(processed.markProcessed(InboxConsumer.NAME, EVENT)).thenReturn(true);
    when(notifications.deleteByComment(500L)).thenReturn(Set.of());

    handler.handle(RecordCommentDeletedCommand.of(EVENT, 500L, 40L, DELETED_AT));

    verify(comments).markDeleted(500L, 40L, DELETED_AT, NOW);
    verify(events, never()).publishEvent(any(Object.class));
  }

  /// 重复的删除事件：什么都不做。
  @Test
  void should_skip_duplicate_event() {
    when(processed.markProcessed(InboxConsumer.NAME, EVENT)).thenReturn(false);

    assertThat(handler.handle(RecordCommentDeletedCommand.of(EVENT, 500L, 40L, DELETED_AT)))
        .isEqualTo(IngestOutcome.DUPLICATE);
    verifyNoInteractions(comments, notifications, events);
  }
}
