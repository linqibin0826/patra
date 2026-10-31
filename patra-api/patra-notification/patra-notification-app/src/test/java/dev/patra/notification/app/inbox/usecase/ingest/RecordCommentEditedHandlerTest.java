package dev.patra.notification.app.inbox.usecase.ingest;

import static dev.patra.notification.app.inbox.usecase.ingest.RecordCommentPublishedHandlerTest.EVENT;
import static dev.patra.notification.app.inbox.usecase.ingest.RecordCommentPublishedHandlerTest.NOW;
import static dev.patra.notification.app.inbox.usecase.ingest.RecordCommentPublishedHandlerTest.facts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.notification.app.inbox.service.NotificationWriter;
import dev.patra.notification.domain.inbox.model.read.NotificationType;
import dev.patra.notification.domain.inbox.model.vo.CommentFacts;
import dev.patra.notification.domain.inbox.model.vo.NotificationDraft;
import dev.patra.notification.domain.inbox.port.repository.CommentContentRepository;
import dev.patra.notification.domain.inbox.port.repository.NotificationRepository;
import dev.patra.notification.domain.inbox.port.repository.SubjectTombstoneRepository;
import dev.patra.tool.core.event.ProcessedEventPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionOperations;

/// 评论已编辑：快照按版本保留最新；只给这条评论第一次 @ 到的人发 @。
class RecordCommentEditedHandlerTest {

  private static final Instant EDITED_AT = Instant.parse("2026-10-31T07:59:30Z");

  private final ProcessedEventPort processed = mock(ProcessedEventPort.class);
  private final SubjectTombstoneRepository tombstones = mock(SubjectTombstoneRepository.class);
  private final CommentContentRepository comments = mock(CommentContentRepository.class);
  private final NotificationRepository notifications = mock(NotificationRepository.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final RecordCommentEditedHandler handler =
      new RecordCommentEditedHandler(
          processed,
          tombstones,
          comments,
          new NotificationWriter(notifications, events),
          TransactionOperations.withoutTransaction(),
          Clock.fixed(NOW, ZoneOffset.UTC));

  /// 新增的 @（去掉作者与被回复的人）各写一条 @ 提醒，排序时间是编辑时刻。
  @Test
  void should_write_mentions_for_added_users_only() {
    when(processed.markProcessed(InboxConsumer.NAME, EVENT)).thenReturn(true);
    when(comments.saveIfNewer(500L, 40L, 2, "改过 <@4> <@5>", NOW)).thenReturn(true);
    when(notifications.insertAll(any())).thenReturn(List.of());

    handler.handle(RecordCommentEditedCommand.of(EVENT, edited(), List.of(5L, 2L, 9L)));

    ArgumentCaptor<List<NotificationDraft>> drafts = ArgumentCaptor.captor();
    verify(notifications).insertAll(drafts.capture());
    assertThat(drafts.getValue())
        .singleElement()
        .satisfies(
            d -> {
              assertThat(d.recipientId()).isEqualTo(5L);
              assertThat(d.type()).isEqualTo(NotificationType.MENTION);
              assertThat(d.occurredAt()).isEqualTo(EDITED_AT);
              assertThat(d.createdAt()).isEqualTo(NOW);
            });
  }

  /// 没有新增的 @：只写快照。
  @Test
  void should_still_save_content_when_no_recipients() {
    when(processed.markProcessed(InboxConsumer.NAME, EVENT)).thenReturn(true);
    when(comments.saveIfNewer(500L, 40L, 2, "改过 <@4> <@5>", NOW)).thenReturn(true);

    assertThat(handler.handle(RecordCommentEditedCommand.of(EVENT, edited(), List.of())))
        .isEqualTo(IngestOutcome.APPLIED);
    verifyNoInteractions(notifications, events);
  }

  /// 评论已删除：丢弃。
  @Test
  void should_discard_when_comment_tombstoned() {
    when(processed.markProcessed(InboxConsumer.NAME, EVENT)).thenReturn(true);
    when(comments.saveIfNewer(500L, 40L, 2, "改过 <@4> <@5>", NOW)).thenReturn(false);

    assertThat(handler.handle(RecordCommentEditedCommand.of(EVENT, edited(), List.of(5L))))
        .isEqualTo(IngestOutcome.DISCARDED);
    verifyNoInteractions(notifications, events);
  }

  /// 第二版正文。
  ///
  /// @return 事实
  private static CommentFacts edited() {
    return facts()
        .version(2)
        .mentionedUserIds(List.of(4L, 5L))
        .plainText("改过 <@4> <@5>")
        .occurredAt(EDITED_AT)
        .build();
  }
}
