package dev.patra.interaction.app.post.usecase.experience;

import static dev.patra.interaction.app.post.PostFixtures.NOW;
import static dev.patra.interaction.app.post.PostFixtures.POST_ID;
import static dev.patra.interaction.app.post.PostFixtures.POST_SUBJECT;
import static dev.patra.interaction.app.post.PostFixtures.post;
import static dev.patra.interaction.app.post.PostFixtures.postView;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.interaction.app.post.RecordingTransactions;
import dev.patra.interaction.app.post.TestUsers;
import dev.patra.interaction.app.post.service.PostRegistry;
import dev.patra.interaction.domain.post.event.PostDeletedEvent;
import dev.patra.interaction.domain.post.exception.PostForbiddenException;
import dev.patra.interaction.domain.post.exception.PostNotFoundException;
import dev.patra.interaction.domain.post.model.enums.PostStatus;
import dev.patra.interaction.domain.post.port.repository.PostRepository;
import dev.patra.interaction.domain.subject.model.enums.SubjectState;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.vo.SubjectSnapshot;
import dev.patra.interaction.domain.subject.port.gateway.PublishGuardGateway;
import dev.patra.interaction.domain.subject.port.gateway.SubjectGateway;
import dev.patra.interaction.domain.subject.port.read.SubjectReadPort;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/// 删除经验（spec 6.3）：作者本人；不查资格；软删并把登记行置为失效（同一事务、传当前快照）；已删的幂等。
class DeleteExperienceHandlerTest {

  private static final long U = 7L;

  private final SubjectGateway gateway = mock(SubjectGateway.class);
  private final SubjectReadPort subjects = mock(SubjectReadPort.class);
  private final PublishGuardGateway guard = mock(PublishGuardGateway.class);
  private final PostRepository posts = mock(PostRepository.class);
  private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
  private final List<String> events = new ArrayList<>();
  private DeleteExperienceHandler handler;

  @BeforeEach
  void setUp() {
    when(subjects.findIdsByKeys(SubjectType.POST, List.of("500")))
        .thenReturn(Map.of("500", POST_SUBJECT));
    when(subjects.findByIds(List.of(POST_SUBJECT)))
        .thenReturn(Map.of(POST_SUBJECT, postView(U, true)));
    doAnswer(invocation -> events.add("delete")).when(posts).markDeleted(any(), any());
    doAnswer(invocation -> events.add("unavailable"))
        .when(gateway)
        .updateLocal(any(), anyLong(), any(), any());
    doAnswer(invocation -> events.add("publish")).when(publisher).publishEvent(any(Object.class));
    handler =
        new DeleteExperienceHandler(
            gateway,
            new PostRegistry(subjects),
            posts,
            TestUsers.signedIn(U),
            new RecordingTransactions(events),
            Clock.fixed(NOW, ZoneOffset.UTC),
            publisher);
  }

  /// 软删、登记行置为失效、发布删除事件都在同一个事务里，登记快照原样传回。
  @Test
  void should_soft_delete_and_mark_registry_unavailable() {
    when(posts.find(POST_ID)).thenReturn(Optional.of(post(U, true, NOW)));

    handler.handle(DeleteExperienceCommand.of(POST_ID.value()));

    assertThat(events)
        .containsExactly(
            RecordingTransactions.BEGIN,
            "delete",
            "unavailable",
            "publish",
            RecordingTransactions.END);
    verify(posts).lockAuthor(U);
    verify(posts).markDeleted(POST_ID, NOW);
    verify(gateway)
        .updateLocal(
            SubjectType.POST,
            POST_ID.value(),
            SubjectState.UNAVAILABLE,
            SubjectSnapshot.of(null, "Journal of X", "2026-03", "J X"));
  }

  /// 登记快照在作者锁之后读：读快照与拿锁之间若有一次改月份的编辑提交，不会把旧月份写回登记表。
  @Test
  void should_read_snapshot_under_lock() {
    when(posts.find(POST_ID)).thenReturn(Optional.of(post(U, true, NOW)));
    doAnswer(invocation -> events.add("lock")).when(posts).lockAuthor(U);
    when(subjects.findIdsByKeys(SubjectType.POST, List.of("500")))
        .thenAnswer(
            invocation -> {
              events.add("snapshot");
              return Map.of("500", POST_SUBJECT);
            });

    handler.handle(DeleteExperienceCommand.of(POST_ID.value()));

    assertThat(events).containsSubsequence("lock", "snapshot", "delete", "unavailable");
  }

  /// 删除事件带上帖子、这条经验自己的登记对象（不是期刊）、作者与删除时刻（与软删同一时刻）。
  @Test
  void should_publish_post_deleted_event() {
    when(posts.find(POST_ID)).thenReturn(Optional.of(post(U, true, NOW)));

    handler.handle(DeleteExperienceCommand.of(POST_ID.value()));

    ArgumentCaptor<PostDeletedEvent> captor = ArgumentCaptor.forClass(PostDeletedEvent.class);
    verify(publisher).publishEvent(captor.capture());
    PostDeletedEvent event = captor.getValue();
    assertThat(event.eventId()).isNotNull();
    assertThat(event)
        .usingRecursiveComparison()
        .ignoringFields("eventId")
        .isEqualTo(
            PostDeletedEvent.builder()
                .eventId(UUID.randomUUID())
                .postId(POST_ID.value())
                .subjectId(POST_SUBJECT)
                .authorId(U)
                .deletedAt(NOW)
                .build());
  }

  /// 已经删了：什么都不做（幂等），也不发事件。
  @Test
  void should_be_idempotent_for_deleted_post() {
    when(posts.find(POST_ID))
        .thenReturn(Optional.of(post(U, true, NOW).toBuilder().status(PostStatus.DELETED).build()));

    handler.handle(DeleteExperienceCommand.of(POST_ID.value()));

    verify(posts, never()).markDeleted(any(), any());
    verify(gateway, never()).updateLocal(any(), anyLong(), any(), any());
    verifyNoInteractions(publisher);
  }

  /// 锁内复核发现已被并发删除：不再写、不发事件。
  @Test
  void should_not_publish_when_deleted_meanwhile() {
    when(posts.find(POST_ID))
        .thenReturn(
            Optional.of(post(U, true, NOW)),
            Optional.of(post(U, true, NOW).toBuilder().status(PostStatus.DELETED).build()));

    handler.handle(DeleteExperienceCommand.of(POST_ID.value()));

    verify(posts, never()).markDeleted(any(), any());
    verifyNoInteractions(publisher);
  }

  /// 删除不查发表资格（冷静期、封禁、未验证都能删自己的）。
  @Test
  void should_not_check_eligibility() {
    when(posts.find(POST_ID)).thenReturn(Optional.of(post(U, true, NOW)));

    handler.handle(DeleteExperienceCommand.of(POST_ID.value()));

    verifyNoInteractions(guard);
  }

  /// 不是作者：403。
  @Test
  void should_reject_foreign_post() {
    when(posts.find(POST_ID)).thenReturn(Optional.of(post(8L, false, NOW)));

    assertThatThrownBy(() -> handler.handle(DeleteExperienceCommand.of(POST_ID.value())))
        .isInstanceOf(PostForbiddenException.class);
    verify(posts, never()).markDeleted(any(), any());
    verifyNoInteractions(publisher);
  }

  /// 不存在：404。
  @Test
  void should_reject_missing_post() {
    when(posts.find(POST_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> handler.handle(DeleteExperienceCommand.of(POST_ID.value())))
        .isInstanceOf(PostNotFoundException.class);
    verifyNoInteractions(publisher);
  }
}
