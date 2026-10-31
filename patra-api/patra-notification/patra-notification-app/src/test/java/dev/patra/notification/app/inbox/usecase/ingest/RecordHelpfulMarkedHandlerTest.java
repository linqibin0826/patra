package dev.patra.notification.app.inbox.usecase.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.notification.domain.inbox.event.NotificationsCreatedEvent;
import dev.patra.notification.domain.inbox.event.UnreadCountsChangedEvent;
import dev.patra.notification.domain.inbox.model.aggregate.HelpfulNotification;
import dev.patra.notification.domain.inbox.model.enums.HelpfulTargetType;
import dev.patra.notification.domain.inbox.model.vo.HelpfulMark;
import dev.patra.notification.domain.inbox.model.vo.HelpfulTarget;
import dev.patra.notification.domain.inbox.model.vo.PostText;
import dev.patra.notification.domain.inbox.model.vo.SubjectOwner;
import dev.patra.notification.domain.inbox.port.repository.CommentContentRepository;
import dev.patra.notification.domain.inbox.port.repository.HelpfulNotificationRepository;
import dev.patra.notification.domain.inbox.port.repository.SubjectTombstoneRepository;
import dev.patra.tool.core.event.ProcessedEventPort;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionOperations;

/// 有帮助已点：去重 → 认目标 → 经验墓碑 → 评论快照（墓碑） → 按合并键并入 → 变了才写、看得见的变化才发事件。
class RecordHelpfulMarkedHandlerTest {

  /// 处理时刻在北京时间 11 月 1 日，点击时刻在 10 月 31 日：合并键按点击时刻取，按处理时刻取就会落到错的一天。
  static final Instant NOW = Instant.parse("2026-11-01T08:00:00Z");
  static final Instant MARKED_AT = Instant.parse("2026-10-30T16:30:00Z");
  static final UUID EVENT = UUID.fromString("00000000-0000-0000-0000-000000000144");
  static final long AUTHOR = 101L;
  static final long C = 11L;
  static final long D = 12L;
  static final LocalDate DAY = LocalDate.of(2026, 10, 31);
  static final HelpfulTarget COMMENT = HelpfulTarget.of(HelpfulTargetType.COMMENT, 500L, 40L);
  static final HelpfulTarget POST = HelpfulTarget.of(HelpfulTargetType.POST, 55L, 41L);

  private final ProcessedEventPort processed = mock(ProcessedEventPort.class);
  private final SubjectTombstoneRepository tombstones = mock(SubjectTombstoneRepository.class);
  private final CommentContentRepository comments = mock(CommentContentRepository.class);
  private final HelpfulNotificationRepository helpful = mock(HelpfulNotificationRepository.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final RecordHelpfulMarkedHandler handler =
      new RecordHelpfulMarkedHandler(
          processed,
          tombstones,
          comments,
          helpful,
          events,
          TransactionOperations.withoutTransaction(),
          Clock.fixed(NOW, ZoneOffset.UTC));

  /// 同一事件第二次到：什么都不做。
  @Test
  void duplicate_event_does_nothing() {
    when(processed.markProcessed(InboxConsumer.NAME, EVENT)).thenReturn(false);

    assertThat(handler.handle(commentMark(C, "评论正文"))).isEqualTo(IngestOutcome.DUPLICATE);
    verifyNoInteractions(tombstones, comments, helpful, events);
  }

  /// 不认识的目标种类：确认并跳过，不抛异常（否则重试三次进死信），去重记录照写。
  @Test
  void unknown_target_type_is_acknowledged_and_skipped() {
    when(processed.markProcessed(InboxConsumer.NAME, EVENT)).thenReturn(true);

    RecordHelpfulMarkedCommand command =
        RecordHelpfulMarkedCommand.builder()
            .eventId(EVENT)
            .targetType("PUBLICATION")
            .targetId(77L)
            .targetAuthorId(AUTHOR)
            .actorId(C)
            .markedAt(MARKED_AT)
            .subjectId(40L)
            .build();

    assertThat(handler.handle(command)).isEqualTo(IngestOutcome.DISCARDED);
    verify(processed).markProcessed(InboxConsumer.NAME, EVENT);
    verifyNoInteractions(tombstones, comments, helpful, events);
  }

  /// 经验已删除：点经验或点它下面的评论都丢弃，不碰评论快照。
  @Test
  void post_tombstone_drops_mark() {
    when(processed.markProcessed(any(), any())).thenReturn(true);
    when(tombstones.exists(41L)).thenReturn(true);
    when(tombstones.exists(40L)).thenReturn(true);

    assertThat(handler.handle(postMark(C, "经验正文"))).isEqualTo(IngestOutcome.DISCARDED);
    assertThat(handler.handle(commentMark(C, "评论正文"))).isEqualTo(IngestOutcome.DISCARDED);
    verifyNoInteractions(comments, helpful, events);
  }

  /// 点评论：用事件正文以版本 0 补快照（已有任何版本都不动，由 saveIfNewer 保证）；点经验不碰快照。
  @Test
  void comment_snapshot_is_seeded_at_version_zero() {
    when(processed.markProcessed(any(), any())).thenReturn(true);
    when(comments.saveIfNewer(500L, 40L, 0, "评论正文", NOW)).thenReturn(true);

    handler.handle(commentMark(C, "评论正文"));
    handler.handle(postMark(C, "经验正文"));

    verify(comments).saveIfNewer(500L, 40L, 0, "评论正文", NOW);
    verify(comments, never()).saveIfNewer(eq(55L), anyLong(), anyInt(), any(), any());
  }

  /// 评论已删除（快照是墓碑）：丢弃。
  @Test
  void comment_tombstone_drops_mark() {
    when(processed.markProcessed(any(), any())).thenReturn(true);
    when(comments.saveIfNewer(500L, 40L, 0, "评论正文", NOW)).thenReturn(false);

    assertThat(handler.handle(commentMark(C, "评论正文"))).isEqualTo(IngestOutcome.DISCARDED);
    verifyNoInteractions(helpful, events);
  }

  /// 评论没有文字：不能用空正文补快照（快照表要求未删除的有正文），只查墓碑；是墓碑丢弃，否则照常写。
  @Test
  void comment_without_text_checks_tombstone_without_seeding() {
    when(processed.markProcessed(any(), any())).thenReturn(true);
    when(comments.isDeleted(500L)).thenReturn(true);

    assertThat(handler.handle(commentMark(C, null))).isEqualTo(IngestOutcome.DISCARDED);
    verify(comments, never()).saveIfNewer(anyLong(), anyLong(), anyInt(), any(), any());
    verifyNoInteractions(helpful);

    when(comments.isDeleted(500L)).thenReturn(false);
    when(helpful.findForUpdate(AUTHOR, COMMENT, DAY, SubjectOwner.of(null, false)))
        .thenReturn(Optional.empty());

    assertThat(handler.handle(commentMark(C, null))).isEqualTo(IngestOutcome.APPLIED);
    verify(helpful).insert(any(), eq(NOW));
  }

  /// 这一天第一次被点：按北京日（UTC 16:30 已是次日）开新条目、写入时刻取时钟；发「未读数变了」，不发「提醒生成」。
  @Test
  void first_mark_of_the_day_opens_entry_and_publishes_counts() {
    when(processed.markProcessed(any(), any())).thenReturn(true);
    when(helpful.findForUpdate(AUTHOR, POST, DAY, SubjectOwner.of(AUTHOR, false)))
        .thenReturn(Optional.empty());

    assertThat(handler.handle(postMark(C, "经验正文"))).isEqualTo(IngestOutcome.APPLIED);

    ArgumentCaptor<HelpfulNotification> written =
        ArgumentCaptor.forClass(HelpfulNotification.class);
    verify(helpful).insert(written.capture(), eq(NOW));
    assertThat(written.getValue().getLatestActorId()).isEqualTo(C);
    assertThat(written.getValue().getDay()).isEqualTo(DAY);
    assertThat(written.getValue().getPostText()).isEqualTo(PostText.of("经验正文", MARKED_AT));
    verify(events).publishEvent(UnreadCountsChangedEvent.of(Set.of(AUTHOR)));
    verify(events, never()).publishEvent(any(NotificationsCreatedEvent.class));
  }

  /// 同一北京日已有条目：并入后写回（人数、最近一位），发事件。
  @Test
  void mark_merges_into_existing_entry_of_same_beijing_day() {
    when(processed.markProcessed(any(), any())).thenReturn(true);
    HelpfulNotification existing = existingPost(Map.of(C, MARKED_AT.minusSeconds(60)), "经验正文");
    when(helpful.findForUpdate(AUTHOR, POST, DAY, SubjectOwner.of(AUTHOR, false)))
        .thenReturn(Optional.of(existing));

    assertThat(handler.handle(postMark(D, "经验正文"))).isEqualTo(IngestOutcome.APPLIED);

    verify(helpful).update(existing, HelpfulMark.of(D, MARKED_AT));
    assertThat(existing.getActorCount()).isEqualTo(2);
    assertThat(existing.getLatestActorId()).isEqualTo(D);
    verify(helpful, never()).insert(any(), any());
    verify(events).publishEvent(UnreadCountsChangedEvent.of(Set.of(AUTHOR)));
  }

  /// 同一个人更晚再点（取消后再点）、正文没变也没往后推：不写、不发。
  @Test
  void unchanged_entry_is_not_written() {
    when(processed.markProcessed(any(), any())).thenReturn(true);
    HelpfulNotification existing =
        HelpfulNotification.restore(
            900L,
            AUTHOR,
            COMMENT,
            SubjectOwner.of(null, false),
            DAY,
            Map.of(C, MARKED_AT.minusSeconds(60)),
            NOW,
            null);
    when(comments.saveIfNewer(500L, 40L, 0, "评论正文", NOW)).thenReturn(true);
    when(helpful.findForUpdate(AUTHOR, COMMENT, DAY, SubjectOwner.of(null, false)))
        .thenReturn(Optional.of(existing));

    assertThat(handler.handle(commentMark(C, "评论正文"))).isEqualTo(IngestOutcome.APPLIED);

    verify(helpful, never()).update(any(), any());
    verifyNoInteractions(events);
  }

  /// 点经验、同一个人更晚再点、正文相同：只推进正文时刻，写库但不发事件。
  @Test
  void stored_only_change_writes_without_event() {
    when(processed.markProcessed(any(), any())).thenReturn(true);
    HelpfulNotification existing = existingPost(Map.of(C, MARKED_AT.minusSeconds(60)), "经验正文");
    when(helpful.findForUpdate(AUTHOR, POST, DAY, SubjectOwner.of(AUTHOR, false)))
        .thenReturn(Optional.of(existing));

    handler.handle(postMark(C, "经验正文"));

    verify(helpful).update(existing, HelpfulMark.of(C, MARKED_AT));
    verifyNoInteractions(events);
  }

  /// 点的人是所在匿名经验的作者本人：写入的条目把他标成匿名。
  @Test
  void owner_hides_anonymous_author() {
    when(processed.markProcessed(any(), any())).thenReturn(true);
    when(comments.saveIfNewer(500L, 40L, 0, "评论正文", NOW)).thenReturn(true);
    SubjectOwner anonymousOwner = SubjectOwner.of(C, true);
    when(helpful.findForUpdate(202L, COMMENT, DAY, anonymousOwner)).thenReturn(Optional.empty());

    handler.handle(
        RecordHelpfulMarkedCommand.builder()
            .eventId(EVENT)
            .targetType("COMMENT")
            .targetId(500L)
            .targetAuthorId(202L)
            .actorId(C)
            .markedAt(MARKED_AT)
            .subjectId(40L)
            .subjectOwnerId(C)
            .subjectOwnerAnonymous(true)
            .targetPlainText("评论正文")
            .build());

    ArgumentCaptor<HelpfulNotification> written =
        ArgumentCaptor.forClass(HelpfulNotification.class);
    verify(helpful).insert(written.capture(), eq(NOW));
    assertThat(written.getValue().isLatestActorAnonymous()).isTrue();
  }

  /// 库里已有的一条经验条目（未读）。
  ///
  /// @param marks 点过的人
  /// @param text 经验正文
  /// @return 条目
  private static HelpfulNotification existingPost(Map<Long, Instant> marks, String text) {
    return HelpfulNotification.restore(
        900L,
        AUTHOR,
        POST,
        SubjectOwner.of(AUTHOR, false),
        DAY,
        marks,
        null,
        PostText.of(text, MARKED_AT.minusSeconds(60)));
  }

  /// 某人点了文献 40 下的评论 500（作者 101）。
  ///
  /// @param actorId 点的人
  /// @param plainText 评论正文，可空
  /// @return 命令
  private static RecordHelpfulMarkedCommand commentMark(long actorId, String plainText) {
    return RecordHelpfulMarkedCommand.builder()
        .eventId(EVENT)
        .targetType("COMMENT")
        .targetId(500L)
        .targetAuthorId(AUTHOR)
        .actorId(actorId)
        .markedAt(MARKED_AT)
        .subjectId(40L)
        .targetPlainText(plainText)
        .build();
  }

  /// 某人点了 101 的经验 55（登记对象 41）。
  ///
  /// @param actorId 点的人
  /// @param plainText 经验正文，可空
  /// @return 命令
  private static RecordHelpfulMarkedCommand postMark(long actorId, String plainText) {
    return RecordHelpfulMarkedCommand.builder()
        .eventId(EVENT)
        .targetType("POST")
        .targetId(55L)
        .targetAuthorId(AUTHOR)
        .actorId(actorId)
        .markedAt(MARKED_AT)
        .subjectId(41L)
        .subjectOwnerId(AUTHOR)
        .subjectOwnerAnonymous(false)
        .targetPlainText(plainText)
        .build();
  }
}
