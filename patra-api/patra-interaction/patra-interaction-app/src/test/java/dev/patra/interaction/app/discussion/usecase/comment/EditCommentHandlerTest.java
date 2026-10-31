package dev.patra.interaction.app.discussion.usecase.comment;

import static dev.patra.interaction.app.discussion.DiscussionFixture.BEGIN;
import static dev.patra.interaction.app.discussion.DiscussionFixture.PUB_ID;
import static dev.patra.interaction.app.discussion.DiscussionFixture.T0;
import static dev.patra.interaction.app.discussion.DiscussionFixture.card;
import static dev.patra.interaction.app.discussion.DiscussionFixture.publication;
import static dev.patra.interaction.app.discussion.DiscussionFixture.recording;
import static dev.patra.interaction.app.discussion.DiscussionFixture.root;
import static dev.patra.interaction.app.discussion.DiscussionFixture.signedIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.interaction.app.discussion.service.CommentViews;
import dev.patra.interaction.app.discussion.service.DiscussionSubjects;
import dev.patra.interaction.app.discussion.service.MentionResolution;
import dev.patra.interaction.app.discussion.usecase.query.CommentView;
import dev.patra.interaction.domain.discussion.event.CommentEditedEvent;
import dev.patra.interaction.domain.discussion.exception.CommentNotFoundException;
import dev.patra.interaction.domain.discussion.exception.CommentNotOwnedException;
import dev.patra.interaction.domain.discussion.model.aggregate.CommentAggregate;
import dev.patra.interaction.domain.discussion.model.enums.CommentState;
import dev.patra.interaction.domain.discussion.model.read.MentionTarget;
import dev.patra.interaction.domain.discussion.model.read.RevisionRecord;
import dev.patra.interaction.domain.discussion.port.mention.MentionDirectoryPort;
import dev.patra.interaction.domain.discussion.port.read.DiscussionReadPort;
import dev.patra.interaction.domain.discussion.port.repository.CommentRepository;
import dev.patra.interaction.domain.subject.exception.PublishIneligibleException;
import dev.patra.interaction.domain.subject.exception.SubjectNotFoundException;
import dev.patra.interaction.domain.subject.model.enums.AccountState;
import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.read.SubjectView;
import dev.patra.interaction.domain.subject.model.vo.PlainText;
import dev.patra.interaction.domain.subject.port.gateway.PublishGuardGateway;
import dev.patra.interaction.domain.subject.port.markdown.PlainTextPort;
import dev.patra.interaction.domain.subject.port.read.SubjectReadPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

/// 编辑（spec 5.5、6.2「编辑的顺序」）：本人、正常、对象没失效才能改；要发表资格、不计频率；当前版本里已有的提及承接；
/// 正文没变什么都不做；超过 5 分钟先存旧版本再改，发布「评论已编辑」。
class EditCommentHandlerTest {

  private static final long EDITOR = 1L;
  private static final long ID = 100L;
  private static final Instant LATER = T0.plus(10, ChronoUnit.MINUTES);

  private final List<String> events = new ArrayList<>();
  /// 假的纯文本转换：把收到的正文与提及名单拼出来，测试据此核对两个参数。
  private static final PlainTextPort ECHO_PLAIN_TEXT =
      (markdown, mentioned) -> PlainText.of(markdown + "|" + new TreeSet<>(mentioned));

  private final SubjectReadPort subjectReads = mock(SubjectReadPort.class);
  private final PublishGuardGateway guard = mock(PublishGuardGateway.class);
  private final MentionDirectoryPort directory = mock(MentionDirectoryPort.class);
  private final CommentRepository comments = mock(CommentRepository.class);
  private final DiscussionReadPort reads = mock(DiscussionReadPort.class);
  private final CommentViews views = mock(CommentViews.class);
  private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

  @BeforeEach
  void setUp() {
    when(subjectReads.findByIds(List.of(PUB_ID))).thenReturn(Map.of(PUB_ID, publication()));
    when(guard.requireEligible(EDITOR)).thenReturn(card(EDITOR));
    when(reads.findById(ID)).thenReturn(Optional.of(root(ID, EDITOR).build()));
    when(views.assemble(any(), any(), any(), any(), any()))
        .thenReturn(List.of(CommentView.builder().commentId(ID).build()));
    stubComment(comment("hi", Set.of(8L)));
  }

  /// 别人的评论：403，不发任何远程调用。
  @Test
  void should_reject_other_users_before_remote_calls() {
    stubComment(comment("hi", Set.of()).toBuilder().authorId(2L).build());

    assertThatThrownBy(() -> handler(LATER).handle(EditCommentCommand.of(ID, "改")))
        .isInstanceOf(CommentNotOwnedException.class);
    verifyNoInteractions(guard, directory);
  }

  /// 占位 404；对象已失效 404。
  @Test
  void should_reject_placeholder_and_unavailable_subject() {
    stubComment(comment("hi", Set.of()).toBuilder().state(CommentState.DELETED).body(null).build());
    assertThatThrownBy(() -> handler(LATER).handle(EditCommentCommand.of(ID, "改")))
        .isInstanceOf(CommentNotFoundException.class);

    stubComment(comment("hi", Set.of()));
    SubjectView gone =
        SubjectView.builder()
            .subjectId(PUB_ID)
            .type(publication().type())
            .key("100")
            .state(SubjectState.UNAVAILABLE)
            .snapshot(publication().snapshot())
            .build();
    when(subjectReads.findByIds(List.of(PUB_ID))).thenReturn(Map.of(PUB_ID, gone));
    assertThatThrownBy(() -> handler(LATER).handle(EditCommentCommand.of(ID, "改")))
        .isInstanceOf(SubjectNotFoundException.class);
  }

  /// 要资格、不计频率；不合格的不进事务。
  @Test
  void should_require_eligibility_without_admitting() {
    handler(LATER).handle(EditCommentCommand.of(ID, "改"));
    verify(guard).requireEligible(EDITOR);
    verify(guard, never()).admit(anyLong(), any());

    events.clear();
    when(guard.requireEligible(EDITOR)).thenThrow(PublishIneligibleException.accountRestricted());
    assertThatThrownBy(() -> handler(LATER).handle(EditCommentCommand.of(ID, "再改")))
        .isInstanceOf(PublishIneligibleException.class);
    assertThat(events).doesNotContain(BEGIN);
  }

  /// 当前版本里已有的人后来被封禁，原样保存时提及照样保留。
  @Test
  void should_carry_over_current_mentions() {
    stubComment(comment("<@8> hi", Set.of(8L)));
    when(directory.resolve(List.of("ann")))
        .thenReturn(Map.of("ann", MentionTarget.of(8L, AccountState.BANNED)));

    handler(LATER).handle(EditCommentCommand.of(ID, "@ann hi there"));

    assertThat(updated().getBody()).isEqualTo("<@8> hi there");
  }

  /// 正文没变：不写、不发事件，返回当前条目。
  @Test
  void should_not_write_or_publish_when_body_unchanged() {
    CommentView view = handler(LATER).handle(EditCommentCommand.of(ID, "hi"));

    assertThat(view.commentId()).isEqualTo(ID);
    verify(comments, never()).updateBody(any());
    verify(comments, never()).appendRevision(anyLong(), any());
    verify(publisher, never()).publishEvent(any(Object.class));
  }

  /// 超过 5 分钟：先存旧版本、再改正文、再发事件；只对第一次 @ 到的人算新增。
  @Test
  void should_archive_then_update_and_publish_after_grace() {
    when(directory.resolve(List.of("bob", "cat")))
        .thenReturn(
            Map.of(
                "bob", MentionTarget.of(8L, AccountState.ACTIVE),
                "cat", MentionTarget.of(9L, AccountState.ACTIVE)));

    handler(LATER).handle(EditCommentCommand.of(ID, "@bob @cat 改"));

    InOrder order = inOrder(comments, publisher);
    order.verify(comments).appendRevision(ID, RevisionRecord.of("hi", T0));
    order.verify(comments).updateBody(any());
    order.verify(publisher).publishEvent(any(Object.class));
    CommentEditedEvent event = published();
    assertThat(event.mentionedUserIds()).containsExactly(8L, 9L);
    assertThat(event.addedMentionedUserIds()).containsExactly(9L);
    assertThat(event.plainText()).isEqualTo("<@8> <@9> 改|[8, 9]");
    assertThat(event.eventId()).isNotNull();
    assertThat(event.version()).isEqualTo(2);
    assertThat(event.editedAt()).isEqualTo(LATER);
    assertThat(updated().getRevisedAt()).isEqualTo(LATER);
  }

  /// 5 分钟内：不存版本，照样发事件（可能加了 @）。
  @Test
  void should_publish_without_revision_within_grace() {
    handler(T0.plus(1, ChronoUnit.MINUTES)).handle(EditCommentCommand.of(ID, "改"));

    verify(comments, never()).appendRevision(anyLong(), any());
    verify(comments).updateBody(any());
    assertThat(published().commentId()).isEqualTo(ID);
  }

  /// 事件带改完后的正文版本，与写进库的一致（PAP-141）。
  @Test
  void should_publish_next_version() {
    stubComment(comment("hi", Set.of(8L)).toBuilder().version(3).build());

    handler(LATER).handle(EditCommentCommand.of(ID, "改"));

    assertThat(updated().getVersion()).isEqualTo(4);
    assertThat(published().version()).isEqualTo(4);
  }

  /// 指定时刻的处理器。
  ///
  /// @param now 现在
  /// @return 处理器
  private EditCommentHandler handler(Instant now) {
    return new EditCommentHandler(
        new DiscussionSubjects(subjectReads),
        guard,
        new MentionResolution(directory),
        comments,
        reads,
        views,
        signedIn(EDITOR),
        recording(events),
        ECHO_PLAIN_TEXT,
        publisher,
        Clock.fixed(now, ZoneOffset.UTC));
  }

  /// 本人在文献下的一条根评论，发表于 T0。
  ///
  /// @param body 正文
  /// @param mentioned 曾经 @ 过的人
  /// @return 评论
  private static CommentAggregate comment(String body, Set<Long> mentioned) {
    return CommentAggregate.builder()
        .commentId(ID)
        .subjectId(PUB_ID)
        .rootId(ID)
        .authorId(EDITOR)
        .state(CommentState.ACTIVE)
        .body(body)
        .mentionedUserIds(mentioned)
        .createdAt(T0)
        .version(CommentAggregate.INITIAL_VERSION)
        .build();
  }

  /// 预读与锁内读都返回这条。
  ///
  /// @param comment 评论
  private void stubComment(CommentAggregate comment) {
    when(comments.find(ID)).thenReturn(Optional.of(comment));
    when(comments.findForEdit(ID)).thenReturn(Optional.of(comment));
  }

  /// 写进库的改后评论。
  ///
  /// @return 评论
  private CommentAggregate updated() {
    ArgumentCaptor<CommentAggregate> captor = ArgumentCaptor.forClass(CommentAggregate.class);
    verify(comments).updateBody(captor.capture());
    return captor.getValue();
  }

  /// 发布的事件。
  ///
  /// @return 事件
  private CommentEditedEvent published() {
    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(publisher).publishEvent(captor.capture());
    return (CommentEditedEvent) captor.getValue();
  }
}
