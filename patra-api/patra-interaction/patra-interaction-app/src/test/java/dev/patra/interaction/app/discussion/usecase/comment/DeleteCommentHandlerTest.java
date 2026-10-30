package dev.patra.interaction.app.discussion.usecase.comment;

import static dev.patra.interaction.app.discussion.DiscussionFixture.BEGIN;
import static dev.patra.interaction.app.discussion.DiscussionFixture.END;
import static dev.patra.interaction.app.discussion.DiscussionFixture.PUB_ID;
import static dev.patra.interaction.app.discussion.DiscussionFixture.T0;
import static dev.patra.interaction.app.discussion.DiscussionFixture.recording;
import static dev.patra.interaction.app.discussion.DiscussionFixture.signedIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.interaction.domain.discussion.event.CommentDeletedEvent;
import dev.patra.interaction.domain.discussion.exception.CommentNotFoundException;
import dev.patra.interaction.domain.discussion.exception.CommentNotOwnedException;
import dev.patra.interaction.domain.discussion.model.aggregate.CommentAggregate;
import dev.patra.interaction.domain.discussion.model.enums.CommentState;
import dev.patra.interaction.domain.discussion.port.repository.CommentRepository;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

/// 删除（spec 5.6）：只有作者能删、不查资格；在楼锁里判断：有回复变占位，没回复整条去掉并级联去掉已经没有回复的
/// 占位祖先；已是占位再删幂等。
class DeleteCommentHandlerTest {

  private static final long ACTOR = 1L;

  private final List<String> events = new ArrayList<>();
  private final CommentRepository comments = mock(CommentRepository.class);
  private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
  private final DeleteCommentHandler handler =
      new DeleteCommentHandler(
          comments, signedIn(ACTOR), recording(events), publisher, Clock.fixed(T0, ZoneOffset.UTC));

  /// 别人的评论 403；不存在 404；都不进事务。
  @Test
  void should_reject_other_users_and_missing_comments() {
    stub(comment(60L, 40L, 50L, 2L, CommentState.ACTIVE), false);
    assertThatThrownBy(() -> handler.handle(DeleteCommentCommand.of(60L)))
        .isInstanceOf(CommentNotOwnedException.class);

    when(comments.find(61L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> handler.handle(DeleteCommentCommand.of(61L)))
        .isInstanceOf(CommentNotFoundException.class);

    assertThat(events).doesNotContain(BEGIN);
  }

  /// 先拿楼锁，再锁行、看有没有回复。
  @Test
  void should_lock_thread_before_reading_children() {
    stub(comment(60L, 40L, 50L, ACTOR, CommentState.ACTIVE), true);

    handler.handle(DeleteCommentCommand.of(60L));

    InOrder order = inOrder(comments);
    order.verify(comments).lockThread(40L);
    order.verify(comments).findForUpdate(60L);
    order.verify(comments).hasReplies(60L);
  }

  /// 有回复：变占位，发事件。
  @Test
  void should_leave_placeholder_when_replied() {
    stub(comment(60L, 40L, 50L, ACTOR, CommentState.ACTIVE), true);

    assertThat(handler.handle(DeleteCommentCommand.of(60L)))
        .isEqualTo(DeleteResult.of(true, List.of()));
    verify(comments).markDeleted(60L, T0);
    verify(comments, never()).delete(anyLong());
    assertThat(published())
        .isEqualTo(
            CommentDeletedEvent.builder()
                .commentId(60L)
                .subjectId(PUB_ID)
                .authorId(ACTOR)
                .placeholder(true)
                .deletedAt(T0)
                .build());
  }

  /// 没回复：整条去掉；父评论、根评论都是已经没有回复的占位，一并去掉；事件只发一次。
  @Test
  void should_remove_and_cascade_orphaned_placeholders() {
    stub(comment(60L, 40L, 50L, ACTOR, CommentState.ACTIVE), false);
    stub(comment(50L, 40L, 40L, 7L, CommentState.DELETED), false);
    stub(comment(40L, 40L, null, 6L, CommentState.DELETED), false);

    assertThat(handler.handle(DeleteCommentCommand.of(60L)))
        .isEqualTo(DeleteResult.of(false, List.of(60L, 50L, 40L)));
    InOrder order = inOrder(comments);
    order.verify(comments).delete(60L);
    order.verify(comments).delete(50L);
    order.verify(comments).delete(40L);
    assertThat(published().placeholder()).isFalse();
  }

  /// 级联停在正常的父评论、或仍有回复的占位。
  @Test
  void should_stop_cascade_at_active_or_replied_parent() {
    stub(comment(60L, 40L, 50L, ACTOR, CommentState.ACTIVE), false);
    stub(comment(50L, 40L, 40L, 7L, CommentState.ACTIVE), false);
    assertThat(handler.handle(DeleteCommentCommand.of(60L)).removedIds()).containsExactly(60L);

    stub(comment(61L, 40L, 51L, ACTOR, CommentState.ACTIVE), false);
    stub(comment(51L, 40L, 40L, 7L, CommentState.DELETED), true);
    assertThat(handler.handle(DeleteCommentCommand.of(61L)).removedIds()).containsExactly(61L);
    verify(comments, never()).delete(50L);
    verify(comments, never()).delete(51L);
  }

  /// 已是占位：幂等，不写、不发事件。
  @Test
  void should_be_idempotent_for_placeholder() {
    stub(comment(60L, 40L, 50L, ACTOR, CommentState.DELETED), true);

    assertThat(handler.handle(DeleteCommentCommand.of(60L)))
        .isEqualTo(DeleteResult.of(true, List.of()));
    verify(comments, never()).markDeleted(anyLong(), any());
    verify(comments, times(0)).delete(anyLong());
    verify(publisher, never()).publishEvent(any(Object.class));
  }

  /// 有子回复的回复变占位：楼内回复数与对象评论数各减一，都在发事件之前。
  @Test
  void should_uncount_reply_turned_placeholder() {
    stub(comment(60L, 40L, 50L, ACTOR, CommentState.ACTIVE), true);

    handler.handle(DeleteCommentCommand.of(60L));

    InOrder order = inOrder(comments, publisher);
    order.verify(comments).markDeleted(60L, T0);
    order.verify(comments).removeReply(40L);
    order.verify(comments).decrementCommentCount(PUB_ID);
    order.verify(publisher).publishEvent(any(Object.class));
    verify(comments, never()).dropThreadStats(anyLong());
  }

  /// 有回复的根评论变占位：只减对象评论数，楼统计不动。
  @Test
  void should_uncount_root_turned_placeholder() {
    stub(comment(40L, 40L, null, ACTOR, CommentState.ACTIVE), true);

    handler.handle(DeleteCommentCommand.of(40L));

    InOrder order = inOrder(comments, publisher);
    order.verify(comments).markDeleted(40L, T0);
    order.verify(comments).decrementCommentCount(PUB_ID);
    order.verify(publisher).publishEvent(any(Object.class));
    verify(comments, never()).removeReply(anyLong());
    verify(comments, never()).dropThreadStats(anyLong());
  }

  /// 没有回复的根评论整条去掉：减对象评论数、发事件，再删它的楼统计行。
  @Test
  void should_drop_thread_stats_when_root_removed() {
    stub(comment(40L, 40L, null, ACTOR, CommentState.ACTIVE), false);

    handler.handle(DeleteCommentCommand.of(40L));

    InOrder order = inOrder(comments, publisher);
    order.verify(comments).delete(40L);
    order.verify(comments).decrementCommentCount(PUB_ID);
    order.verify(publisher).publishEvent(any(Object.class));
    order.verify(comments).dropThreadStats(40L);
    verify(comments, never()).removeReply(anyLong());
  }

  /// 删回复级联去掉了占位根：楼内与对象各只减一次（占位本来不计数），最后删楼统计行。
  @Test
  void should_drop_thread_stats_when_cascade_reaches_root() {
    stub(comment(60L, 40L, 40L, ACTOR, CommentState.ACTIVE), false);
    stub(comment(40L, 40L, null, 6L, CommentState.DELETED), false);

    handler.handle(DeleteCommentCommand.of(60L));

    InOrder order = inOrder(comments, publisher);
    order.verify(comments).delete(60L);
    order.verify(comments).removeReply(40L);
    order.verify(comments).decrementCommentCount(PUB_ID);
    order.verify(publisher).publishEvent(any(Object.class));
    order.verify(comments).delete(40L);
    order.verify(comments).dropThreadStats(40L);
    verify(comments, times(1)).removeReply(anyLong());
    verify(comments, times(1)).decrementCommentCount(any());
  }

  /// 已是占位：统计一概不动。
  @Test
  void should_not_touch_stats_when_already_placeholder() {
    stub(comment(60L, 40L, 50L, ACTOR, CommentState.DELETED), true);

    handler.handle(DeleteCommentCommand.of(60L));

    verify(comments, never()).removeReply(anyLong());
    verify(comments, never()).dropThreadStats(anyLong());
    verify(comments, never()).decrementCommentCount(any());
  }

  /// 统计改动都在事务里。
  @Test
  void should_update_stats_inside_transaction() {
    stub(comment(60L, 40L, 50L, ACTOR, CommentState.ACTIVE), true);
    doAnswer(
            invocation -> {
              events.add("removeReply");
              return null;
            })
        .when(comments)
        .removeReply(40L);

    handler.handle(DeleteCommentCommand.of(60L));

    assertThat(events).containsSubsequence(BEGIN, "removeReply", END);
  }

  /// 预读与锁内读都返回这条，并设定有没有回复。
  ///
  /// @param comment 评论
  /// @param hasReplies 有没有直接回复
  private void stub(CommentAggregate comment, boolean hasReplies) {
    when(comments.find(comment.getCommentId())).thenReturn(Optional.of(comment));
    when(comments.findForUpdate(comment.getCommentId())).thenReturn(Optional.of(comment));
    when(comments.hasReplies(comment.getCommentId())).thenReturn(hasReplies);
  }

  /// 一条评论。
  ///
  /// @param id 评论 ID
  /// @param rootId 根评论
  /// @param parentId 父评论，根评论为 `null`
  /// @param authorId 作者
  /// @param state 状态
  /// @return 评论
  private static CommentAggregate comment(
      long id, long rootId, Long parentId, long authorId, CommentState state) {
    return CommentAggregate.builder()
        .commentId(id)
        .subjectId(PUB_ID)
        .rootId(rootId)
        .parentId(parentId)
        .authorId(authorId)
        .state(state)
        .body(state == CommentState.ACTIVE ? "正文" : null)
        .createdAt(T0)
        .build();
  }

  /// 发布的事件（只有一个）。
  ///
  /// @return 事件
  private CommentDeletedEvent published() {
    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(publisher).publishEvent(captor.capture());
    return (CommentDeletedEvent) captor.getValue();
  }
}
