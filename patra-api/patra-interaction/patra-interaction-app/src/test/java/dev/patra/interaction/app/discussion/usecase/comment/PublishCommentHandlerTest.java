package dev.patra.interaction.app.discussion.usecase.comment;

import static dev.patra.interaction.app.discussion.DiscussionFixture.BEGIN;
import static dev.patra.interaction.app.discussion.DiscussionFixture.END;
import static dev.patra.interaction.app.discussion.DiscussionFixture.POST_ID;
import static dev.patra.interaction.app.discussion.DiscussionFixture.PUB_ID;
import static dev.patra.interaction.app.discussion.DiscussionFixture.card;
import static dev.patra.interaction.app.discussion.DiscussionFixture.post;
import static dev.patra.interaction.app.discussion.DiscussionFixture.publication;
import static dev.patra.interaction.app.discussion.DiscussionFixture.recording;
import static dev.patra.interaction.app.discussion.DiscussionFixture.root;
import static dev.patra.interaction.app.discussion.DiscussionFixture.signedIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.interaction.app.discussion.service.CommentViews;
import dev.patra.interaction.app.discussion.service.DiscussionSubjects;
import dev.patra.interaction.app.discussion.service.MentionResolution;
import dev.patra.interaction.app.discussion.usecase.query.CommentView;
import dev.patra.interaction.domain.discussion.event.CommentPublishedEvent;
import dev.patra.interaction.domain.discussion.exception.InvalidCommentException;
import dev.patra.interaction.domain.discussion.exception.InvalidDiscussionRequestException;
import dev.patra.interaction.domain.discussion.exception.ReplyTargetGoneException;
import dev.patra.interaction.domain.discussion.model.aggregate.CommentAggregate;
import dev.patra.interaction.domain.discussion.model.enums.CommentState;
import dev.patra.interaction.domain.discussion.model.read.MentionTarget;
import dev.patra.interaction.domain.discussion.model.vo.NewComment;
import dev.patra.interaction.domain.discussion.port.mention.MentionDirectoryPort;
import dev.patra.interaction.domain.discussion.port.read.DiscussionReadPort;
import dev.patra.interaction.domain.discussion.port.repository.CommentRepository;
import dev.patra.interaction.domain.subject.exception.PublishIneligibleException;
import dev.patra.interaction.domain.subject.model.enums.AccountState;
import dev.patra.interaction.domain.subject.model.enums.PublishKind;
import dev.patra.interaction.domain.subject.model.enums.SubjectCapability;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.read.UserCard;
import dev.patra.interaction.domain.subject.model.vo.SubjectOwner;
import dev.patra.interaction.domain.subject.port.gateway.PublishGuardGateway;
import dev.patra.interaction.domain.subject.port.gateway.SubjectGateway;
import dev.patra.interaction.domain.subject.port.read.SubjectReadPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import org.springframework.context.ApplicationEventPublisher;

/// 发表根评论与回复（spec 6.2「发表的顺序」）：本地校验先于任何远程调用；资格先于对象解析；事务里先计数、回复先拿楼锁
/// 再重读父评论，写完在同一事务里发布事件；响应用自己的名片。
class PublishCommentHandlerTest {

  private static final long AUTHOR = 1L;
  private static final long NEW_ID = 900L;
  private static final Instant NOW = Instant.parse("2026-10-27T08:00:00.123456789Z");
  private static final Instant NOW_MICROS = Instant.parse("2026-10-27T08:00:00.123456Z");

  private final List<String> events = new ArrayList<>();
  private final SubjectReadPort subjectReads = mock(SubjectReadPort.class);
  private final SubjectGateway gateway = mock(SubjectGateway.class);
  private final PublishGuardGateway guard = mock(PublishGuardGateway.class);
  private final MentionDirectoryPort directory = mock(MentionDirectoryPort.class);
  private final CommentRepository comments = mock(CommentRepository.class);
  private final DiscussionReadPort reads = mock(DiscussionReadPort.class);
  private final CommentViews views = mock(CommentViews.class);
  private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
  private final UserCard self = card(AUTHOR);
  private final CommentView assembled = CommentView.builder().commentId(NEW_ID).build();
  private PublishCommentHandler handler;

  @BeforeEach
  void setUp() {
    handler =
        new PublishCommentHandler(
            new DiscussionSubjects(subjectReads),
            gateway,
            guard,
            new MentionResolution(directory),
            comments,
            reads,
            views,
            signedIn(AUTHOR),
            recording(events),
            publisher,
            Clock.fixed(NOW, ZoneOffset.UTC));
    when(guard.requireEligible(AUTHOR)).thenAnswer(record("eligible", self));
    doAnswer(record("admit", null)).when(guard).admit(AUTHOR, PublishKind.COMMENT);
    when(gateway.resolveForInteraction(SubjectType.PUBLICATION, "100", SubjectCapability.DISCUSS))
        .thenAnswer(record("resolve", PUB_ID));
    when(subjectReads.findByIds(List.of(PUB_ID))).thenReturn(Map.of(PUB_ID, publication()));
    when(comments.insert(any())).thenAnswer(record("insert", NEW_ID));
    doAnswer(record("lock", null)).when(comments).lockThread(anyLong());
    doAnswer(record("recordReply", null)).when(comments).recordReply(anyLong(), any());
    doAnswer(record("count", null)).when(comments).incrementCommentCount(any());
    doAnswer(record("publish", null)).when(publisher).publishEvent(any(Object.class));
    when(reads.findById(NEW_ID)).thenReturn(Optional.of(root(NEW_ID, AUTHOR).build()));
    when(views.assemble(any(), any(), any(), any(), any())).thenReturn(List.of(assembled));
  }

  /// 未知类型、空正文、不合法的父评论 ID、101 个候选：都在任何远程调用之前拒绝。
  @Test
  void should_validate_locally_before_any_remote_call() {
    String tooMany =
        IntStream.rangeClosed(1, 101).mapToObj(i -> "@user-" + i).collect(Collectors.joining(" "));

    assertThatThrownBy(() -> handler.handle(PublishCommentCommand.of("book", "100", null, "hi")))
        .isInstanceOf(InvalidDiscussionRequestException.class);
    assertThatThrownBy(
            () -> handler.handle(PublishCommentCommand.of("publication", "100", null, "  ")))
        .isInstanceOf(InvalidCommentException.class);
    assertThatThrownBy(
            () -> handler.handle(PublishCommentCommand.of("publication", "100", 0L, "hi")))
        .isInstanceOf(InvalidDiscussionRequestException.class);
    assertThatThrownBy(
            () -> handler.handle(PublishCommentCommand.of("publication", "100", null, tooMany)))
        .isInstanceOf(InvalidCommentException.class);
    verifyNoInteractions(guard, gateway, directory);
  }

  /// 资格不符时不解析对象（不为不能发表的人登记文献）。
  @Test
  void should_check_eligibility_before_resolving_subject() {
    when(guard.requireEligible(AUTHOR)).thenThrow(PublishIneligibleException.emailUnverified());

    assertThatThrownBy(
            () -> handler.handle(PublishCommentCommand.of("publication", "100", null, "hi")))
        .isInstanceOf(PublishIneligibleException.class);
    verifyNoInteractions(gateway);
  }

  /// 根评论：资格 → 解析对象 → 事务（计数 → 插入 → 对象评论数 +1 → 发布）；事件字段齐全，时刻截到微秒；
  /// 响应用自己的名片。
  @Test
  void should_publish_root_comment_in_one_transaction() {
    when(directory.resolve(List.of("bob")))
        .thenReturn(Map.of("bob", MentionTarget.of(7L, AccountState.ACTIVE)));

    CommentView view =
        handler.handle(PublishCommentCommand.of("publication", "100", null, "@bob 你好"));

    assertThat(view).isSameAs(assembled);
    assertThat(events)
        .containsExactly("eligible", "resolve", BEGIN, "admit", "insert", "count", "publish", END);
    verify(comments).incrementCommentCount(PUB_ID);
    verify(comments, never()).recordReply(anyLong(), any());
    NewComment inserted = captureInsert();
    assertThat(inserted.rootId()).isNull();
    assertThat(inserted.parentId()).isNull();
    assertThat(inserted.subjectId()).isEqualTo(PUB_ID);
    assertThat(inserted.body()).isEqualTo("<@7> 你好");
    assertThat(inserted.mentionedUserIds()).containsExactly(7L);
    assertThat(inserted.createdAt()).isEqualTo(NOW_MICROS);
    assertThat(capturePublished())
        .isEqualTo(
            CommentPublishedEvent.builder()
                .commentId(NEW_ID)
                .subjectId(PUB_ID)
                .subjectType(SubjectType.PUBLICATION)
                .rootCommentId(NEW_ID)
                .authorId(AUTHOR)
                .authorAnonymous(false)
                .mentionedUserIds(List.of(7L))
                .body("<@7> 你好")
                .publishedAt(NOW_MICROS)
                .build());
    verify(views)
        .assemble(
            List.of(root(NEW_ID, AUTHOR).build()),
            publication(),
            AUTHOR,
            Map.of(AUTHOR, self),
            Set.of());
  }

  /// 回复：预读父评论，事务里先拿楼锁再重读，插入时根是父评论所在的楼；插入后楼内回复数、对象评论数各 +1，
  /// 都在发事件之前。
  @Test
  void should_lock_thread_and_recheck_parent_for_replies() {
    when(comments.find(50L)).thenAnswer(record("find", Optional.of(reply(50L, 40L, 6L))));

    handler.handle(PublishCommentCommand.of("publication", "100", 50L, "同意"));

    assertThat(events)
        .containsExactly(
            "eligible",
            "resolve",
            "find",
            BEGIN,
            "admit",
            "lock",
            "find",
            "insert",
            "recordReply",
            "count",
            "publish",
            END);
    verify(comments).lockThread(40L);
    verify(comments).recordReply(40L, NOW_MICROS);
    verify(comments).incrementCommentCount(PUB_ID);
    NewComment inserted = captureInsert();
    assertThat(inserted.rootId()).isEqualTo(40L);
    assertThat(inserted.parentId()).isEqualTo(50L);
    CommentPublishedEvent event = capturePublished();
    assertThat(event.rootCommentId()).isEqualTo(40L);
    assertThat(event.parentCommentId()).isEqualTo(50L);
    assertThat(event.parentAuthorId()).isEqualTo(6L);
  }

  /// 锁内读到父评论已是占位的回复：409，不插入、不发事件。
  @Test
  void should_reject_reply_to_deleted_reply() {
    CommentAggregate deleted =
        reply(50L, 40L, 6L).toBuilder().state(CommentState.DELETED).body(null).build();
    when(comments.find(50L)).thenReturn(Optional.of(reply(50L, 40L, 6L)), Optional.of(deleted));

    assertThatThrownBy(
            () -> handler.handle(PublishCommentCommand.of("publication", "100", 50L, "同意")))
        .isInstanceOf(ReplyTargetGoneException.class);
    verify(comments, never()).insert(any());
    verify(publisher, never()).publishEvent(any(Object.class));
  }

  /// 回复已成占位的根评论（「回复这一楼」）：能发，事件里没有被回复者。
  @Test
  void should_allow_reply_to_placeholder_root_without_parent_author() {
    CommentAggregate placeholderRoot =
        CommentAggregate.builder()
            .commentId(40L)
            .subjectId(PUB_ID)
            .rootId(40L)
            .authorId(6L)
            .state(CommentState.DELETED)
            .createdAt(NOW)
            .build();
    when(comments.find(40L)).thenReturn(Optional.of(placeholderRoot));

    handler.handle(PublishCommentCommand.of("publication", "100", 40L, "接着说"));

    assertThat(captureInsert().rootId()).isEqualTo(40L);
    assertThat(capturePublished().parentAuthorId()).isNull();
  }

  /// 父评论不存在 409；父评论不在这个对象下 422；都在事务之前。
  @Test
  void should_reject_missing_parent_and_parent_elsewhere() {
    when(comments.find(50L)).thenReturn(Optional.empty());
    assertThatThrownBy(
            () -> handler.handle(PublishCommentCommand.of("publication", "100", 50L, "x")))
        .isInstanceOf(ReplyTargetGoneException.class);

    CommentAggregate elsewhere = reply(51L, 40L, 6L).toBuilder().subjectId(POST_ID).build();
    when(comments.find(51L)).thenReturn(Optional.of(elsewhere));
    assertThatThrownBy(
            () -> handler.handle(PublishCommentCommand.of("publication", "100", 51L, "x")))
        .isInstanceOf(InvalidDiscussionRequestException.class);

    assertThat(events).doesNotContain(BEGIN);
  }

  /// 投稿经验的匿名所有者自己评论：事件标匿名，带上所有者。
  @Test
  void should_flag_anonymous_owner_in_event() {
    when(gateway.resolveForInteraction(SubjectType.POST, "500", SubjectCapability.DISCUSS))
        .thenReturn(POST_ID);
    when(subjectReads.findByIds(List.of(POST_ID))).thenReturn(Map.of(POST_ID, post(AUTHOR, true)));

    handler.handle(PublishCommentCommand.of("post", "500", null, "补充一点"));

    CommentPublishedEvent event = capturePublished();
    assertThat(event.subjectType()).isEqualTo(SubjectType.POST);
    assertThat(event.subjectOwner()).isEqualTo(SubjectOwner.of(AUTHOR, true));
    assertThat(event.authorAnonymous()).isTrue();
    verify(directory, never()).resolve(anyCollection());
    verify(views).assemble(any(), eq(post(AUTHOR, true)), eq(AUTHOR), any(), eq(Set.of()));
  }

  /// 一条正常的回复（作者 6，楼 `rootId`）。
  ///
  /// @param id 评论 ID
  /// @param rootId 根评论
  /// @param authorId 作者
  /// @return 评论
  private static CommentAggregate reply(long id, long rootId, long authorId) {
    return CommentAggregate.builder()
        .commentId(id)
        .subjectId(PUB_ID)
        .rootId(rootId)
        .parentId(rootId)
        .authorId(authorId)
        .state(CommentState.ACTIVE)
        .body("父评论")
        .mentionedUserIds(Set.of())
        .createdAt(NOW)
        .build();
  }

  /// 记下一个事件并返回给定结果的应答。
  ///
  /// @param event 事件名
  /// @param result 返回值
  /// @param <T> 返回类型
  /// @return 应答
  private <T> Answer<T> record(String event, T result) {
    return invocation -> {
      events.add(event);
      return result;
    };
  }

  /// 插入的新评论。
  ///
  /// @return 新评论
  private NewComment captureInsert() {
    ArgumentCaptor<NewComment> inserted = ArgumentCaptor.forClass(NewComment.class);
    verify(comments).insert(inserted.capture());
    return inserted.getValue();
  }

  /// 发布的事件。
  ///
  /// @return 事件
  private CommentPublishedEvent capturePublished() {
    ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
    verify(publisher).publishEvent(published.capture());
    return (CommentPublishedEvent) published.getValue();
  }
}
