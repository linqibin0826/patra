package dev.patra.notification.app.inbox.usecase.ingest;

import static dev.patra.notification.app.inbox.usecase.ingest.RecordCommentPublishedHandlerTest.EVENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.notification.domain.inbox.event.UnreadCountsChangedEvent;
import dev.patra.notification.domain.inbox.port.repository.CommentContentRepository;
import dev.patra.notification.domain.inbox.port.repository.NotificationRepository;
import dev.patra.notification.domain.inbox.port.repository.SubjectTombstoneRepository;
import dev.patra.tool.core.event.ProcessedEventPort;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionOperations;

/// 投稿经验已删除：记对象墓碑，删它下面的全部提醒与评论快照。
class RecordPostDeletedHandlerTest {

  private static final Instant DELETED_AT = Instant.parse("2026-10-31T07:59:50Z");

  private final ProcessedEventPort processed = mock(ProcessedEventPort.class);
  private final SubjectTombstoneRepository tombstones = mock(SubjectTombstoneRepository.class);
  private final CommentContentRepository comments = mock(CommentContentRepository.class);
  private final NotificationRepository notifications = mock(NotificationRepository.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final RecordPostDeletedHandler handler =
      new RecordPostDeletedHandler(
          processed,
          tombstones,
          comments,
          notifications,
          events,
          TransactionOperations.withoutTransaction());

  /// 墓碑、提醒、快照一起处理；被删了提醒的人未读数变了。
  @Test
  void should_tombstone_subject_and_delete_everything_under_it() {
    when(processed.markProcessed(InboxConsumer.NAME, EVENT)).thenReturn(true);
    when(notifications.deleteBySubject(41L)).thenReturn(Set.of(3L, 5L));

    assertThat(handler.handle(RecordPostDeletedCommand.of(EVENT, 41L, DELETED_AT)))
        .isEqualTo(IngestOutcome.APPLIED);

    verify(tombstones).save(41L, DELETED_AT);
    verify(comments).deleteBySubject(41L);
    verify(events).publishEvent(UnreadCountsChangedEvent.of(Set.of(3L, 5L)));
  }

  /// 重复的删除事件：什么都不做。
  @Test
  void should_skip_duplicate_event() {
    when(processed.markProcessed(InboxConsumer.NAME, EVENT)).thenReturn(false);

    assertThat(handler.handle(RecordPostDeletedCommand.of(EVENT, 41L, DELETED_AT)))
        .isEqualTo(IngestOutcome.DUPLICATE);
    verifyNoInteractions(tombstones, comments, notifications, events);
  }
}
