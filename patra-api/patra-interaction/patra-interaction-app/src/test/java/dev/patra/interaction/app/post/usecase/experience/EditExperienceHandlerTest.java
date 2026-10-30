package dev.patra.interaction.app.post.usecase.experience;

import static dev.patra.interaction.app.post.PostFixtures.JOURNAL;
import static dev.patra.interaction.app.post.PostFixtures.NOW;
import static dev.patra.interaction.app.post.PostFixtures.POST_ID;
import static dev.patra.interaction.app.post.PostFixtures.POST_SUBJECT;
import static dev.patra.interaction.app.post.PostFixtures.card;
import static dev.patra.interaction.app.post.PostFixtures.input;
import static dev.patra.interaction.app.post.PostFixtures.journalView;
import static dev.patra.interaction.app.post.PostFixtures.post;
import static dev.patra.interaction.app.post.PostFixtures.postView;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.interaction.app.post.RecordingTransactions;
import dev.patra.interaction.app.post.TestUsers;
import dev.patra.interaction.app.post.service.ExperienceViews;
import dev.patra.interaction.app.post.service.PostRegistry;
import dev.patra.interaction.app.post.usecase.query.ExperienceDetailView;
import dev.patra.interaction.domain.post.exception.DuplicateSubmissionException;
import dev.patra.interaction.domain.post.exception.PostForbiddenException;
import dev.patra.interaction.domain.post.exception.PostNotFoundException;
import dev.patra.interaction.domain.post.model.aggregate.PostAggregate;
import dev.patra.interaction.domain.post.model.enums.PostStatus;
import dev.patra.interaction.domain.post.model.vo.PostId;
import dev.patra.interaction.domain.post.model.vo.PostRevision;
import dev.patra.interaction.domain.post.port.read.PostReadPort;
import dev.patra.interaction.domain.post.port.repository.PostRepository;
import dev.patra.interaction.domain.subject.exception.SubjectErrorTrait;
import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.vo.SubjectSnapshot;
import dev.patra.interaction.domain.subject.port.gateway.PublishGuardGateway;
import dev.patra.interaction.domain.subject.port.gateway.SubjectGateway;
import dev.patra.interaction.domain.subject.port.read.SubjectReadPort;
import dev.patra.interaction.domain.subject.port.user.UserDirectoryPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/// 编辑经验（spec 6.2）：作者本人、未删除；也查发表资格但不计频率；锁内查重（排除自己）；5 分钟内覆盖、之后记版本；
/// 投稿月份变了刷新登记快照。
class EditExperienceHandlerTest {

  private static final long U = 7L;

  private final SubjectGateway gateway = mock(SubjectGateway.class);
  private final SubjectReadPort subjects = mock(SubjectReadPort.class);
  private final PublishGuardGateway guard = mock(PublishGuardGateway.class);
  private final PostRepository posts = mock(PostRepository.class);
  private final List<String> events = new ArrayList<>();
  private EditExperienceHandler handler;

  @BeforeEach
  void setUp() {
    when(guard.requireEligible(U))
        .thenAnswer(
            invocation -> {
              events.add("eligible");
              return card(U, true);
            });
    when(subjects.findIdsByKeys(SubjectType.POST, List.of("500")))
        .thenReturn(Map.of("500", POST_SUBJECT));
    when(subjects.findByIds(List.of(POST_SUBJECT)))
        .thenReturn(Map.of(POST_SUBJECT, postView(U, false)));
    when(subjects.findByIds(List.of(JOURNAL))).thenReturn(Map.of(JOURNAL, journalView()));
    when(posts.findActiveBySubmission(anyLong(), any(), any())).thenReturn(Optional.empty());
    handler = handlerAt(NOW);
  }

  /// 不存在 404；已删除 404 + SUBJECT_UNAVAILABLE；不是作者 403；都不查资格。
  @Test
  void should_reject_missing_deleted_and_foreign_posts() {
    when(posts.find(POST_ID)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> handler.handle(command()))
        .isInstanceOfSatisfying(
            PostNotFoundException.class,
            ex ->
                assertThat(ex.getErrorTraits())
                    .doesNotContain(SubjectErrorTrait.SUBJECT_UNAVAILABLE));

    when(posts.find(POST_ID)).thenReturn(Optional.of(deleted(post(U, false, NOW))));
    assertThatThrownBy(() -> handler.handle(command()))
        .isInstanceOfSatisfying(
            PostNotFoundException.class,
            ex -> assertThat(ex.getErrorTraits()).contains(SubjectErrorTrait.SUBJECT_UNAVAILABLE));

    when(posts.find(POST_ID)).thenReturn(Optional.of(post(8L, false, NOW)));
    assertThatThrownBy(() -> handler.handle(command())).isInstanceOf(PostForbiddenException.class);

    verifyNoInteractions(guard);
  }

  /// 编辑也查资格（事务外），但不计发表频率。
  @Test
  void should_check_eligibility_outside_transaction_without_admit() {
    when(posts.find(POST_ID)).thenReturn(Optional.of(post(U, false, NOW)));

    handler.handle(command());

    assertThat(events.indexOf("eligible")).isLessThan(events.indexOf(RecordingTransactions.BEGIN));
    verify(guard, never()).admit(anyLong(), any());
  }

  /// 发布后 5 分钟内改：只覆盖，不记版本。
  @Test
  void should_overwrite_within_grace_without_revision() {
    PostAggregate post = post(U, false, NOW.minus(Duration.ofMinutes(4)));
    when(posts.find(POST_ID)).thenReturn(Optional.of(post));

    ExperienceDetailView detail = handler.handle(command());

    verify(posts).lockAuthor(U);
    verify(posts).update(any(), any());
    verify(posts, never()).insertRevision(any(), any());
    assertThat(detail.experience().edited()).isFalse();
  }

  /// 过了 5 分钟：被替换的旧内容记成一版，成文时刻是发布时刻。
  @Test
  void should_record_revision_after_grace() {
    Instant created = NOW.minus(Duration.ofMinutes(10));
    PostAggregate post = post(U, false, created);
    when(posts.find(POST_ID)).thenReturn(Optional.of(post));

    ExperienceDetailView detail = handler.handle(command());

    ArgumentCaptor<PostRevision> revision = ArgumentCaptor.forClass(PostRevision.class);
    verify(posts).insertRevision(eq(POST_ID), revision.capture());
    assertThat(revision.getValue().fields()).isEqualTo(post.fields());
    assertThat(revision.getValue().writtenAt()).isEqualTo(created);
    assertThat(revision.getValue().replacedAt()).isEqualTo(NOW);
    assertThat(detail.experience().edited()).isTrue();
  }

  /// 内容一字没变：什么都不写。
  @Test
  void should_do_nothing_when_unchanged() {
    when(posts.find(POST_ID))
        .thenReturn(Optional.of(post(U, false, NOW.minus(Duration.ofHours(1)))));

    handler.handle(EditExperienceCommand.of(POST_ID.value(), input().body("正文").build()));

    verify(posts, never()).update(any(), any());
    verify(posts, never()).insertRevision(any(), any());
    verify(gateway, never()).updateLocal(any(), anyLong(), any(), any());
  }

  /// 改成自己另一条经验的投稿日期：409；查到的是自己则不算重复。
  @Test
  void should_reject_edit_onto_another_posts_date() {
    when(posts.find(POST_ID)).thenReturn(Optional.of(post(U, false, NOW)));
    when(posts.findActiveBySubmission(U, JOURNAL, LocalDate.parse("2026-03-05")))
        .thenReturn(Optional.of(PostId.of(77L)));

    assertThatThrownBy(
            () ->
                handler.handle(
                    EditExperienceCommand.of(
                        POST_ID.value(), input().submittedOn("2026-03-05").build())))
        .isInstanceOfSatisfying(
            DuplicateSubmissionException.class,
            ex -> assertThat(ex.existingPostId()).isEqualTo(PostId.of(77L)));
    verify(posts, never()).update(any(), any());

    when(posts.findActiveBySubmission(U, JOURNAL, LocalDate.parse("2026-03-02")))
        .thenReturn(Optional.of(POST_ID));
    handler.handle(command());
    verify(posts).update(any(), any());
  }

  /// 投稿月份变了：登记快照换成新月份（期刊全称与缩写不变）；月份没变不刷新。
  @Test
  void should_update_registry_snapshot_when_month_changes() {
    when(posts.find(POST_ID)).thenReturn(Optional.of(post(U, false, NOW)));

    handler.handle(
        EditExperienceCommand.of(
            POST_ID.value(),
            input().submittedOn("2026-04-01").firstDecisionOn("2026-04-13").build()));

    verify(gateway)
        .updateLocal(
            SubjectType.POST,
            POST_ID.value(),
            SubjectState.ACTIVE,
            SubjectSnapshot.of(null, "Journal of X", "2026-04", "J X"));

    handler.handle(command());
    verify(gateway).updateLocal(any(), anyLong(), any(), any());
  }

  /// 月份变没变按锁内重读的内容判：别的标签页刚把它改到 4 月，这次改回 3 月，登记快照要跟着回到 3 月；快照也在锁内读。
  @Test
  void should_decide_snapshot_from_post_read_under_lock() {
    PostAggregate march = post(U, false, NOW);
    PostAggregate april =
        march.toBuilder()
            .fields(march.fields().toBuilder().submittedOn(LocalDate.parse("2026-04-01")).build())
            .build();
    when(posts.find(POST_ID)).thenReturn(Optional.of(march), Optional.of(april));
    doAnswer(
            invocation -> {
              events.add("lock");
              return null;
            })
        .when(posts)
        .lockAuthor(U);
    when(subjects.findIdsByKeys(SubjectType.POST, List.of("500")))
        .thenAnswer(
            invocation -> {
              events.add("snapshot");
              return Map.of("500", POST_SUBJECT);
            });

    handler.handle(command());

    verify(gateway)
        .updateLocal(
            SubjectType.POST,
            POST_ID.value(),
            SubjectState.ACTIVE,
            SubjectSnapshot.of(null, "Journal of X", "2026-03", "J X"));
    assertThat(events.indexOf("lock")).isLessThan(events.indexOf("snapshot"));
  }

  /// 编辑不经发表守卫，同一作者改投稿日期与写新经验之间只靠作者锁串行：查重必须在拿锁之后。
  @Test
  void should_check_duplicates_under_author_lock() {
    when(posts.find(POST_ID)).thenReturn(Optional.of(post(U, false, NOW)));

    handler.handle(command());

    InOrder order = inOrder(posts);
    order.verify(posts).lockAuthor(U);
    order.verify(posts).find(POST_ID);
    order.verify(posts).findActiveBySubmission(U, JOURNAL, LocalDate.parse("2026-03-02"));
  }

  /// 锁内再取一次：期间被删了就 404。
  @Test
  void should_recheck_after_lock() {
    PostAggregate post = post(U, false, NOW);
    when(posts.find(POST_ID)).thenReturn(Optional.of(post), Optional.of(deleted(post)));

    assertThatThrownBy(() -> handler.handle(command())).isInstanceOf(PostNotFoundException.class);
    verify(posts, never()).update(any(), any());
  }

  /// 改正文的命令（字段同原样，正文换了）。
  ///
  /// @return 命令
  private static EditExperienceCommand command() {
    return EditExperienceCommand.of(POST_ID.value(), input().body("改过的正文").build());
  }

  /// 已删除的同一条。
  ///
  /// @param post 经验
  /// @return 已删除的经验
  private static PostAggregate deleted(PostAggregate post) {
    return post.toBuilder().status(PostStatus.DELETED).build();
  }

  /// 给定时刻的处理器。
  ///
  /// @param now 现在
  /// @return 处理器
  private EditExperienceHandler handlerAt(Instant now) {
    return new EditExperienceHandler(
        gateway,
        new PostRegistry(subjects),
        guard,
        posts,
        new ExperienceViews(mock(UserDirectoryPort.class), subjects, mock(PostReadPort.class)),
        TestUsers.signedIn(U),
        new RecordingTransactions(events),
        Clock.fixed(now, ZoneOffset.UTC));
  }
}
