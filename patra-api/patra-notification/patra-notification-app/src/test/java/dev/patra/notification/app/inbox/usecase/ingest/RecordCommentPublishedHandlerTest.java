package dev.patra.notification.app.inbox.usecase.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.notification.app.inbox.service.NotificationWriter;
import dev.patra.notification.domain.inbox.event.NotificationsCreatedEvent;
import dev.patra.notification.domain.inbox.event.UnreadCountsChangedEvent;
import dev.patra.notification.domain.inbox.model.read.CreatedNotification;
import dev.patra.notification.domain.inbox.model.read.NotificationType;
import dev.patra.notification.domain.inbox.model.vo.CommentFacts;
import dev.patra.notification.domain.inbox.model.vo.NotificationDraft;
import dev.patra.notification.domain.inbox.port.repository.CommentContentRepository;
import dev.patra.notification.domain.inbox.port.repository.NotificationRepository;
import dev.patra.notification.domain.inbox.port.repository.SubjectTombstoneRepository;
import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import dev.patra.notification.domain.kernel.model.enums.SubjectType;
import dev.patra.tool.core.event.ProcessedEventPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionOperations;

/// 评论已发表：去重 → 对象墓碑 → 评论快照（墓碑） → 按规则写提醒 → 发事件。
class RecordCommentPublishedHandlerTest {

  static final Instant NOW = Instant.parse("2026-10-31T08:00:00Z");
  static final Instant PUBLISHED_AT = Instant.parse("2026-10-31T07:59:00Z");
  static final UUID EVENT = UUID.fromString("00000000-0000-0000-0000-000000000001");

  private final ProcessedEventPort processed = mock(ProcessedEventPort.class);
  private final SubjectTombstoneRepository tombstones = mock(SubjectTombstoneRepository.class);
  private final CommentContentRepository comments = mock(CommentContentRepository.class);
  private final NotificationRepository notifications = mock(NotificationRepository.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final RecordCommentPublishedHandler handler =
      new RecordCommentPublishedHandler(
          processed,
          tombstones,
          comments,
          new NotificationWriter(notifications, events),
          TransactionOperations.withoutTransaction(),
          Clock.fixed(NOW, ZoneOffset.UTC));

  /// 同一事件第二次到：什么都不做。
  @Test
  void should_skip_duplicate_event() {
    when(processed.markProcessed(InboxConsumer.NAME, EVENT)).thenReturn(false);

    assertThat(handler.handle(command())).isEqualTo(IngestOutcome.DUPLICATE);
    verifyNoInteractions(tombstones, comments, notifications, events);
  }

  /// 经验已删除：丢弃，不写快照也不写提醒。
  @Test
  void should_discard_when_subject_tombstoned() {
    when(processed.markProcessed(InboxConsumer.NAME, EVENT)).thenReturn(true);
    when(tombstones.exists(40L)).thenReturn(true);

    assertThat(handler.handle(command())).isEqualTo(IngestOutcome.DISCARDED);
    verifyNoInteractions(comments, notifications, events);
  }

  /// 评论已删除（删除先到）：丢弃，不写提醒。
  @Test
  void should_discard_when_comment_tombstoned() {
    when(processed.markProcessed(InboxConsumer.NAME, EVENT)).thenReturn(true);
    when(comments.saveIfNewer(500L, 40L, 1, "谢谢 <@4>", NOW)).thenReturn(false);

    assertThat(handler.handle(command())).isEqualTo(IngestOutcome.DISCARDED);
    verifyNoInteractions(notifications, events);
  }

  /// 正常：按规则给被回复的人与 @ 到的人写提醒，排序时间是发表时刻、写入时刻是现在；真写入了就发两个事件。
  @Test
  void should_write_recipients_and_publish_events() {
    when(processed.markProcessed(InboxConsumer.NAME, EVENT)).thenReturn(true);
    when(comments.saveIfNewer(anyLong(), anyLong(), anyInt(), anyString(), any())).thenReturn(true);
    List<CreatedNotification> created =
        List.of(
            created(11L, 2L, NotificationCategory.REPLY),
            created(12L, 4L, NotificationCategory.MENTION));
    when(notifications.insertAll(any())).thenReturn(created);

    assertThat(handler.handle(command())).isEqualTo(IngestOutcome.APPLIED);

    ArgumentCaptor<List<NotificationDraft>> drafts = ArgumentCaptor.captor();
    verify(notifications).insertAll(drafts.capture());
    assertThat(drafts.getValue())
        .containsExactly(
            draft(2L, NotificationType.COMMENT_REPLY), draft(4L, NotificationType.MENTION));
    verify(events).publishEvent(NotificationsCreatedEvent.of(created));
    verify(events).publishEvent(UnreadCountsChangedEvent.of(Set.of(2L, 4L)));
  }

  /// 唯一索引全挡掉了（例如重投成了新事件）：不发事件。
  @Test
  void should_not_publish_when_nothing_inserted() {
    when(processed.markProcessed(InboxConsumer.NAME, EVENT)).thenReturn(true);
    when(comments.saveIfNewer(anyLong(), anyLong(), anyInt(), anyString(), any())).thenReturn(true);
    when(notifications.insertAll(any())).thenReturn(List.of());

    handler.handle(command());

    verify(events, never()).publishEvent(any(Object.class));
  }

  /// 没有收件人（自己回复自己）：快照照写，不调写提醒。
  @Test
  void should_save_content_even_without_recipients() {
    when(processed.markProcessed(InboxConsumer.NAME, EVENT)).thenReturn(true);
    when(comments.saveIfNewer(anyLong(), anyLong(), anyInt(), anyString(), any())).thenReturn(true);

    handler.handle(
        RecordCommentPublishedCommand.of(
            EVENT, facts().parentAuthorId(9L).mentionedUserIds(List.of()).build()));

    verify(comments).saveIfNewer(500L, 40L, 1, "谢谢 <@4>", NOW);
    verifyNoInteractions(notifications, events);
  }

  /// 命令：作者 9 在文献 40 下回复 2 的评论，@ 了 4。
  ///
  /// @return 命令
  private static RecordCommentPublishedCommand command() {
    return RecordCommentPublishedCommand.of(EVENT, facts().build());
  }

  /// 事实。
  ///
  /// @return 构建器
  static CommentFacts.CommentFactsBuilder facts() {
    return CommentFacts.builder()
        .commentId(500L)
        .version(1)
        .subjectId(40L)
        .subjectType(SubjectType.PUBLICATION)
        .parentCommentId(100L)
        .parentAuthorId(2L)
        .authorId(9L)
        .authorAnonymous(true)
        .mentionedUserIds(List.of(4L))
        .plainText("谢谢 <@4>")
        .occurredAt(PUBLISHED_AT);
  }

  /// 期望的草稿。
  ///
  /// @param recipient 收件人
  /// @param type 类型
  /// @return 草稿
  private static NotificationDraft draft(long recipient, NotificationType type) {
    return NotificationDraft.builder()
        .recipientId(recipient)
        .type(type)
        .actorId(9L)
        .actorAnonymous(true)
        .subjectId(40L)
        .commentId(500L)
        .occurredAt(PUBLISHED_AT)
        .createdAt(NOW)
        .build();
  }

  /// 写入的结果。
  ///
  /// @param id 提醒 ID
  /// @param recipient 收件人
  /// @param category 类别
  /// @return 结果
  static CreatedNotification created(long id, long recipient, NotificationCategory category) {
    return CreatedNotification.builder()
        .notificationId(id)
        .recipientId(recipient)
        .category(category)
        .occurredAt(PUBLISHED_AT)
        .createdAt(NOW)
        .build();
  }
}
