package dev.patra.interaction.app.discussion.usecase.query;

import static dev.patra.interaction.app.discussion.DiscussionFixture.POST_ID;
import static dev.patra.interaction.app.discussion.DiscussionFixture.PUB_ID;
import static dev.patra.interaction.app.discussion.DiscussionFixture.T0;
import static dev.patra.interaction.app.discussion.DiscussionFixture.card;
import static dev.patra.interaction.app.discussion.DiscussionFixture.publication;
import static dev.patra.interaction.app.discussion.DiscussionFixture.reply;
import static dev.patra.interaction.app.discussion.DiscussionFixture.root;
import static dev.patra.interaction.app.discussion.DiscussionFixture.signedIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.interaction.app.discussion.service.CommentViews;
import dev.patra.interaction.app.discussion.service.DiscussionSubjects;
import dev.patra.interaction.domain.discussion.exception.CommentNotFoundException;
import dev.patra.interaction.domain.discussion.exception.CommentNotOwnedException;
import dev.patra.interaction.domain.discussion.exception.InvalidDiscussionRequestException;
import dev.patra.interaction.domain.discussion.model.enums.CommentState;
import dev.patra.interaction.domain.discussion.model.enums.ThreadSort;
import dev.patra.interaction.domain.discussion.model.read.CommentRecord;
import dev.patra.interaction.domain.discussion.model.read.RevisionRecord;
import dev.patra.interaction.domain.discussion.model.vo.ThreadCursor;
import dev.patra.interaction.domain.discussion.port.read.DiscussionReadPort;
import dev.patra.interaction.domain.subject.model.enums.AccountState;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.read.UserCard;
import dev.patra.interaction.domain.subject.port.read.SubjectReadPort;
import dev.patra.tool.core.security.CurrentUserPort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/// 讨论的读（spec 6.1）：未登记为空页；根评论分页加每楼前 3 条回复；单楼带目标；楼内回复只对根评论；修改历史最新在前；
/// 编辑原文只给作者、换回当前用户名。
class DiscussionQueryServiceTest {

  private static final long VIEWER = 5L;

  private final SubjectReadPort subjectReads = mock(SubjectReadPort.class);
  private final DiscussionReadPort reads = mock(DiscussionReadPort.class);
  private final CommentViews views = mock(CommentViews.class);

  @BeforeEach
  void setUp() {
    when(subjectReads.findIdsByKeys(SubjectType.PUBLICATION, List.of("100")))
        .thenReturn(Map.of("100", PUB_ID));
    when(subjectReads.findByIds(List.of(PUB_ID))).thenReturn(Map.of(PUB_ID, publication()));
    when(views.assemble(anyList(), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              List<CommentRecord> records = invocation.getArgument(0);
              return records.stream()
                  .map(r -> CommentView.builder().commentId(r.commentId()).build())
                  .toList();
            });
  }

  /// 还没人讨论过：空页，不查讨论表。
  @Test
  void should_return_empty_page_for_unregistered_subject() {
    when(subjectReads.findIdsByKeys(SubjectType.PUBLICATION, List.of("100"))).thenReturn(Map.of());

    ThreadPageView page = service(VIEWER).threads("publication", "100", null, null, null);

    assertThat(page.items()).isEmpty();
    assertThat(page.nextCursor()).isNull();
    verifyNoInteractions(reads);
  }

  /// 每页多取一条判下一页；每楼多取一条判「还有更多回复」；一页一次拼装。
  @Test
  void should_page_threads_with_three_reply_previews() {
    CommentRecord r1 = root(1L, 2L).build();
    CommentRecord r2 = root(2L, 2L).createdAt(T0.plusSeconds(1)).build();
    List<CommentRecord> replies =
        LongStream.rangeClosed(11, 14)
            .mapToObj(id -> reply(id, 1L, 1L, 2L, 3L).createdAt(T0.plusSeconds(id)).build())
            .toList();
    when(reads.findRoots(PUB_ID, ThreadSort.ASC, null, 2)).thenReturn(List.of(r1, r2));
    when(reads.findReplyPreviews(List.of(1L), 4)).thenReturn(Map.of(1L, replies));

    ThreadPageView page = service(VIEWER).threads("publication", "100", null, null, 1);

    assertThat(page.items()).hasSize(1);
    ThreadView thread = page.items().getFirst();
    assertThat(thread.root().commentId()).isEqualTo(1L);
    assertThat(thread.replies()).extracting(CommentView::commentId).containsExactly(11L, 12L, 13L);
    assertThat(thread.hasMoreReplies()).isTrue();
    assertThat(thread.repliesCursor()).isEqualTo(ThreadCursor.of(T0.plusSeconds(13), 13L).format());
    assertThat(page.nextCursor()).isEqualTo(ThreadCursor.of(T0, 1L).format());
    verify(views, times(1))
        .assemble(
            List.of(r1, replies.get(0), replies.get(1), replies.get(2)),
            publication(),
            VIEWER,
            Map.of());
  }

  /// 分页参数不合法：422，不查库。
  @Test
  void should_reject_bad_paging_params() {
    DiscussionQueryService service = service(VIEWER);

    assertThatThrownBy(() -> service.threads("publication", "100", null, null, 0))
        .isInstanceOf(InvalidDiscussionRequestException.class);
    assertThatThrownBy(() -> service.threads("publication", "100", null, null, 51))
        .isInstanceOf(InvalidDiscussionRequestException.class);
    assertThatThrownBy(() -> service.threads("publication", "100", "hot", null, null))
        .isInstanceOf(InvalidDiscussionRequestException.class);
    assertThatThrownBy(() -> service.threads("publication", "100", null, "x", null))
        .isInstanceOf(InvalidDiscussionRequestException.class);
    verifyNoInteractions(reads);
  }

  /// 单楼：整楼加目标；目标在别的对象下 404。
  @Test
  void should_return_single_thread_with_target() {
    CommentRecord target = reply(12L, 10L, 11L, 3L, 4L).build();
    when(reads.findById(12L)).thenReturn(Optional.of(target));
    when(reads.findThread(10L))
        .thenReturn(List.of(root(10L, 2L).build(), reply(11L, 10L, 10L, 2L, 3L).build(), target));

    SingleThreadView view = service(VIEWER).thread("publication", "100", 12L);

    assertThat(view.targetId()).isEqualTo(12L);
    assertThat(view.thread().root().commentId()).isEqualTo(10L);
    assertThat(view.thread().replies())
        .extracting(CommentView::commentId)
        .containsExactly(11L, 12L);
    assertThat(view.thread().hasMoreReplies()).isFalse();

    when(reads.findById(13L)).thenReturn(Optional.of(root(13L, 2L).subjectId(POST_ID).build()));
    assertThatThrownBy(() -> service(VIEWER).thread("publication", "100", 13L))
        .isInstanceOf(CommentNotFoundException.class);
  }

  /// 楼内回复只对根评论。
  @Test
  void should_404_replies_for_non_root() {
    when(reads.findById(11L)).thenReturn(Optional.of(reply(11L, 10L, 10L, 2L, 3L).build()));

    assertThatThrownBy(() -> service(VIEWER).replies(11L, null, null))
        .isInstanceOf(CommentNotFoundException.class);
  }

  /// 修改历史：当前版本在前，最初版本在后；提及取全部版本的并集；没改过只有一版；占位 404。
  @Test
  void should_build_revisions_newest_first() {
    Instant r1 = T0.plusSeconds(600);
    Instant r2 = T0.plusSeconds(1200);
    when(reads.findById(1L))
        .thenReturn(Optional.of(root(1L, 2L).body("当前 <@7>").revisedAt(r2).build()));
    when(reads.findRevisions(1L))
        .thenReturn(List.of(RevisionRecord.of("b1 <@8>", r1), RevisionRecord.of("b0", T0)));

    RevisionsView revisions = service(VIEWER).revisions(1L);

    assertThat(revisions.versions())
        .containsExactly(
            RevisionRecord.of("当前 <@7>", r2),
            RevisionRecord.of("b1 <@8>", r1),
            RevisionRecord.of("b0", T0));
    verify(views).mentions(List.of(7L, 8L), VIEWER);

    when(reads.findById(2L)).thenReturn(Optional.of(root(2L, 2L).build()));
    when(reads.findRevisions(2L)).thenReturn(List.of());
    assertThat(service(VIEWER).revisions(2L).versions())
        .containsExactly(RevisionRecord.of("正文", T0));

    when(reads.findById(3L))
        .thenReturn(Optional.of(root(3L, 2L).state(CommentState.DELETED).body(null).build()));
    assertThatThrownBy(() -> service(VIEWER).revisions(3L))
        .isInstanceOf(CommentNotFoundException.class);
  }

  /// 编辑原文只给作者：标记换回当前用户名（不看可见性），已注销的换成 `@已注销用户`。
  @Test
  void should_give_author_source_with_current_usernames() {
    when(reads.findById(1L)).thenReturn(Optional.of(root(1L, VIEWER).body("hi <@7> <@8>").build()));
    UserCard renamed =
        UserCard.builder()
            .userId(7L)
            .username("new-name")
            .displayName("D7")
            .state(AccountState.ACTIVE)
            .profileVisible(false)
            .build();
    when(views.cards(List.of(7L, 8L), VIEWER))
        .thenReturn(Map.of(7L, renamed, 8L, card(8L, AccountState.DELETED, false)));

    assertThat(service(VIEWER).source(1L)).isEqualTo("hi @new-name @已注销用户");
    assertThatThrownBy(() -> service(6L).source(1L)).isInstanceOf(CommentNotOwnedException.class);
  }

  /// 以某个访客的身份。
  ///
  /// @param viewer 访客
  /// @return 服务
  private DiscussionQueryService service(long viewer) {
    CurrentUserPort currentUser = signedIn(viewer);
    return new DiscussionQueryService(
        new DiscussionSubjects(subjectReads), reads, views, currentUser);
  }
}
