package dev.patra.interaction.app.reaction.listener;

import static dev.patra.interaction.app.reaction.ReactionFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.interaction.domain.discussion.event.CommentDeletedEvent;
import dev.patra.interaction.domain.post.event.PostDeletedEvent;
import dev.patra.interaction.domain.reaction.model.enums.HelpfulTargetType;
import dev.patra.interaction.domain.reaction.model.vo.HelpfulTarget;
import dev.patra.interaction.domain.reaction.port.repository.HelpfulRepository;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;

/// 删除联动（spec §5）：评论变占位或整条去掉、经验删除时，在同一事务里删掉它收到的有帮助、扣回获赞。
class HelpfulTargetDeletedListenerTest {

  private static final HelpfulTarget COMMENT = HelpfulTarget.of(HelpfulTargetType.COMMENT, 42L);
  private static final HelpfulTarget POST = HelpfulTarget.of(HelpfulTargetType.POST, 500L);

  private final HelpfulRepository helpful = mock(HelpfulRepository.class);
  private final HelpfulTargetDeletedListener listener = new HelpfulTargetDeletedListener(helpful);

  /// 先拿目标锁再删；获赞按用户 ID 升序扣回。
  @Test
  void should_remove_comment_helpful_on_delete() {
    when(helpful.deleteAllOn(COMMENT)).thenReturn(Map.of(9L, 2, 3L, 1));

    listener.on(commentDeleted(false));

    InOrder order = inOrder(helpful);
    order.verify(helpful).lockTarget(COMMENT);
    order.verify(helpful).deleteAllOn(COMMENT);
    order.verify(helpful).adjustReceived(3L, -1L);
    order.verify(helpful).adjustReceived(9L, -2L);
  }

  /// 变成占位的评论同样清掉。
  @Test
  void should_remove_comment_helpful_when_it_becomes_placeholder() {
    when(helpful.deleteAllOn(COMMENT)).thenReturn(Map.of(9L, 1));

    listener.on(commentDeleted(true));

    verify(helpful).lockTarget(COMMENT);
    verify(helpful).adjustReceived(9L, -1L);
  }

  /// 经验按帖子 ID 清；没有计入任何人的（匿名经验）不扣获赞。
  @Test
  void should_remove_post_helpful_on_delete() {
    when(helpful.deleteAllOn(POST)).thenReturn(Map.of());

    listener.on(
        PostDeletedEvent.builder()
            .eventId(UUID.randomUUID())
            .postId(500L)
            .subjectId(SubjectId.of(77L))
            .authorId(7L)
            .deletedAt(NOW)
            .build());

    verify(helpful).lockTarget(POST);
    verify(helpful).deleteAllOn(POST);
    verify(helpful, never()).adjustReceived(anyLong(), anyLong());
  }

  /// 数据库异常包成 500，原始异常留在原因链里（删除一起回滚）。
  @Test
  void should_wrap_unexpected_failure() {
    DataIntegrityViolationException failure = new DataIntegrityViolationException("x");
    when(helpful.deleteAllOn(COMMENT)).thenThrow(failure);

    assertThatThrownBy(() -> listener.on(commentDeleted(false)))
        .isInstanceOfSatisfying(
            HelpfulWriteFailedException.class, ex -> assertThat(ex.getCause()).isSameAs(failure));
  }

  /// 评论 42 的删除事件。
  ///
  /// @param placeholder 是否留下占位
  /// @return 事件
  private static CommentDeletedEvent commentDeleted(boolean placeholder) {
    return CommentDeletedEvent.builder()
        .eventId(UUID.randomUUID())
        .commentId(42L)
        .version(1)
        .subjectId(SubjectId.of(11L))
        .authorId(7L)
        .placeholder(placeholder)
        .deletedAt(NOW)
        .build();
  }
}
