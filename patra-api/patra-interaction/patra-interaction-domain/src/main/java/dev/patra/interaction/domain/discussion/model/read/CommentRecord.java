package dev.patra.interaction.domain.discussion.model.read;

import dev.patra.interaction.domain.discussion.model.enums.CommentState;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.time.Instant;
import lombok.Builder;

/// 读出来的一条评论，带上父评论的作者与状态（给「回复 @X」）、有没有直接回复（给删除确认的文案）和有帮助数。
///
/// @param commentId 评论 ID
/// @param subjectId 对象
/// @param rootId 所在楼的根评论；根评论是它自己
/// @param parentId 回复的是哪一条；根评论为 `null`
/// @param authorId 作者
/// @param state 状态
/// @param body 正文（存储形式）；占位为 `null`
/// @param createdAt 发表时刻
/// @param revisedAt 当前版本由一次记版本的编辑产生的时刻；没有修改记录为 `null`
/// @param parentAuthorId 父评论的作者；根评论为 `null`
/// @param parentState 父评论的状态；根评论为 `null`
/// @param hasReplies 有没有直接回复
/// @param helpfulCount 有帮助数（`reaction.target_stats`，没有统计行为 0）
@Builder
public record CommentRecord(
    long commentId,
    SubjectId subjectId,
    long rootId,
    Long parentId,
    long authorId,
    CommentState state,
    String body,
    Instant createdAt,
    Instant revisedAt,
    Long parentAuthorId,
    CommentState parentState,
    boolean hasReplies,
    int helpfulCount) {

  /// 是否根评论。
  ///
  /// @return 没有父评论时为 `true`
  public boolean isRoot() {
    return parentId == null;
  }

  /// 是否占位。
  ///
  /// @return 已删除留占位时为 `true`
  public boolean isDeleted() {
    return state == CommentState.DELETED;
  }

  /// 是否有修改记录（门户据此显示「已编辑」）。
  ///
  /// @return 有记版本的编辑时为 `true`
  public boolean edited() {
    return revisedAt != null;
  }
}
