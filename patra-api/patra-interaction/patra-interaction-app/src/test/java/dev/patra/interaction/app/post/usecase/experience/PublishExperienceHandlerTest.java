package dev.patra.interaction.app.post.usecase.experience;

import static dev.patra.interaction.app.post.PostFixtures.JOURNAL;
import static dev.patra.interaction.app.post.PostFixtures.JOURNAL_KEY;
import static dev.patra.interaction.app.post.PostFixtures.NOW;
import static dev.patra.interaction.app.post.PostFixtures.POST_ID;
import static dev.patra.interaction.app.post.PostFixtures.POST_SUBJECT;
import static dev.patra.interaction.app.post.PostFixtures.card;
import static dev.patra.interaction.app.post.PostFixtures.input;
import static dev.patra.interaction.app.post.PostFixtures.journalView;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.interaction.app.post.RecordingTransactions;
import dev.patra.interaction.app.post.TestUsers;
import dev.patra.interaction.app.post.service.ExperienceViews;
import dev.patra.interaction.app.post.usecase.query.ExperienceDetailView;
import dev.patra.interaction.domain.post.exception.DuplicateSubmissionException;
import dev.patra.interaction.domain.post.exception.InvalidExperienceException;
import dev.patra.interaction.domain.post.exception.PostErrorTrait;
import dev.patra.interaction.domain.post.model.aggregate.PostAggregate;
import dev.patra.interaction.domain.post.model.vo.PostId;
import dev.patra.interaction.domain.post.port.repository.PostRepository;
import dev.patra.interaction.domain.subject.model.enums.PublishKind;
import dev.patra.interaction.domain.subject.model.enums.SubjectCapability;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.read.UserCard;
import dev.patra.interaction.domain.subject.model.vo.SubjectOwner;
import dev.patra.interaction.domain.subject.model.vo.SubjectSnapshot;
import dev.patra.interaction.domain.subject.port.gateway.PublishGuardGateway;
import dev.patra.interaction.domain.subject.port.gateway.SubjectGateway;
import dev.patra.interaction.domain.subject.port.read.SubjectReadPort;
import dev.patra.interaction.domain.subject.port.user.UserDirectoryPort;
import dev.patra.tool.core.error.field.FieldViolation;
import dev.patra.tool.core.error.trait.StandardErrorTrait;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;

/// 写经验（spec 6.1）：先校验；事务外解析期刊、查资格；事务里先过频率、拿作者锁、查重，再写入并登记成 `POST` 对象。
class PublishExperienceHandlerTest {

  private static final long U = 7L;

  private final SubjectGateway gateway = mock(SubjectGateway.class);
  private final SubjectReadPort subjects = mock(SubjectReadPort.class);
  private final PublishGuardGateway guard = mock(PublishGuardGateway.class);
  private final UserDirectoryPort users = mock(UserDirectoryPort.class);
  private final PostRepository posts = mock(PostRepository.class);
  private final List<String> events = new ArrayList<>();
  private final UserCard self = card(U, true);
  private PublishExperienceHandler handler;

  @BeforeEach
  void setUp() {
    when(gateway.resolveForInteraction(
            SubjectType.JOURNAL, JOURNAL_KEY, SubjectCapability.SUBMISSION_EXPERIENCE))
        .thenAnswer(record("resolve", JOURNAL));
    when(guard.requireEligible(U)).thenAnswer(record("eligible", self));
    doAnswer(record("admit", null)).when(guard).admit(U, PublishKind.POST);
    doAnswer(record("lock", null)).when(posts).lockAuthor(U);
    when(posts.nextId()).thenReturn(POST_ID);
    when(posts.findActiveBySubmission(U, JOURNAL, LocalDate.parse("2026-03-02")))
        .thenAnswer(record("dup-check", Optional.empty()));
    doAnswer(record("insert", null)).when(posts).insert(any());
    when(gateway.registerLocal(any(), any(Long.class), any(), any()))
        .thenAnswer(record("register", POST_SUBJECT));
    when(subjects.findByIds(List.of(JOURNAL))).thenReturn(Map.of(JOURNAL, journalView()));
    handler =
        new PublishExperienceHandler(
            gateway,
            subjects,
            guard,
            posts,
            new ExperienceViews(users, subjects),
            TestUsers.signedIn(U),
            new RecordingTransactions(events),
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  /// 远程调用都在事务外；事务里第一个调用是频率，登记在写入之后、同一事务里。
  @Test
  void should_publish_in_order() {
    handler.handle(PublishExperienceCommand.of(JOURNAL_KEY, input().build(), true));

    assertThat(events)
        .containsExactly(
            "resolve",
            "eligible",
            RecordingTransactions.BEGIN,
            "admit",
            "lock",
            "dup-check",
            "insert",
            "register",
            RecordingTransactions.END);
    verify(gateway)
        .registerLocal(
            SubjectType.POST,
            POST_ID.value(),
            SubjectOwner.of(U, true),
            SubjectSnapshot.of(null, "Journal of X", "2026-03", "J X"));
    ArgumentCaptor<PostAggregate> inserted = ArgumentCaptor.forClass(PostAggregate.class);
    verify(posts).insert(inserted.capture());
    assertThat(inserted.getValue().authorId()).isEqualTo(U);
    assertThat(inserted.getValue().anonymous()).isTrue();
    assertThat(inserted.getValue().subjectId()).isEqualTo(JOURNAL);
    assertThat(inserted.getValue().createdAt()).isEqualTo(NOW);
  }

  /// 发布时刻截到微秒（PG 的精度），201 响应与之后读回来的值一致。
  @Test
  void should_truncate_created_at_to_micros() {
    handler =
        new PublishExperienceHandler(
            gateway,
            subjects,
            guard,
            posts,
            new ExperienceViews(users, subjects),
            TestUsers.signedIn(U),
            new RecordingTransactions(events),
            Clock.fixed(NOW.plusNanos(123_456_789), ZoneOffset.UTC));

    ExperienceDetailView detail =
        handler.handle(PublishExperienceCommand.of(JOURNAL_KEY, input().build(), false));

    assertThat(detail.experience().createdAt()).isEqualTo(NOW.plusNanos(123_456_000));
  }

  /// 字段不合法：不碰 catalog、identity，也不开事务。
  @Test
  void should_validate_before_remote_calls() {
    assertThatThrownBy(
            () ->
                handler.handle(
                    PublishExperienceCommand.of(
                        JOURNAL_KEY, input().submittedOn(null).build(), false)))
        .isInstanceOf(InvalidExperienceException.class);

    verifyNoInteractions(gateway, guard, posts);
    assertThat(events).isEmpty();
  }

  /// 同刊同投稿日期已有一条：409，带已有 ID 与 `submittedOn` 字段错误，不写入、不登记。
  @Test
  void should_reject_duplicate_with_existing_id() {
    when(posts.findActiveBySubmission(U, JOURNAL, LocalDate.parse("2026-03-02")))
        .thenReturn(Optional.of(PostId.of(77L)));

    assertThatThrownBy(
            () -> handler.handle(PublishExperienceCommand.of(JOURNAL_KEY, input().build(), false)))
        .isInstanceOfSatisfying(
            DuplicateSubmissionException.class,
            ex -> {
              assertThat(ex.existingPostId()).isEqualTo(PostId.of(77L));
              assertThat(ex.getErrorTraits())
                  .containsExactlyInAnyOrder(
                      StandardErrorTrait.CONFLICT, PostErrorTrait.DUPLICATE_SUBMISSION);
              assertThat(ex.getFieldViolations())
                  .extracting(FieldViolation::field, FieldViolation::code)
                  .containsExactly(tuple("submittedOn", "DUPLICATE"));
            });
    verify(posts, never()).insert(any());
    verify(gateway, never()).registerLocal(any(), any(Long.class), any(), any());
  }

  /// 返回完整详情：本人写的，作者是资格回查拿到的名片，不再调 identity。
  @Test
  void should_return_detail_with_self_card() {
    ExperienceDetailView detail =
        handler.handle(PublishExperienceCommand.of(JOURNAL_KEY, input().build(), true));

    assertThat(detail.postId()).isEqualTo(POST_ID);
    assertThat(detail.deleted()).isFalse();
    assertThat(detail.journal().key()).isEqualTo(JOURNAL_KEY);
    assertThat(detail.experience().mine()).isTrue();
    assertThat(detail.experience().anonymous()).isTrue();
    assertThat(detail.experience().author().userId()).isEqualTo(U);
    verifyNoInteractions(users);
  }

  /// 记一个事件并返回给定值。
  ///
  /// @param event 事件
  /// @param value 返回值
  /// @return 应答
  private Answer<Object> record(String event, Object value) {
    return invocation -> {
      events.add(event);
      return value;
    };
  }
}
