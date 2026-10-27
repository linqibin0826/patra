package dev.patra.interaction.adapter.discussion.rest.response;

import dev.patra.interaction.app.discussion.usecase.comment.DeleteResult;
import java.util.List;

/// 删除的结果：门户按它把这条换成占位，或者移除这条与级联去掉的占位。
///
/// @param placeholder 留下了占位
/// @param removedIds 整条去掉的评论（这条在前，之后是级联去掉的占位）；留占位时为空
public record DeleteCommentResponse(boolean placeholder, List<Long> removedIds) {

  /// 防御性拷贝。
  public DeleteCommentResponse {
    removedIds = List.copyOf(removedIds);
  }

  /// 从结果转换。
  ///
  /// @param result 结果
  /// @return 响应
  public static DeleteCommentResponse from(DeleteResult result) {
    return new DeleteCommentResponse(result.placeholder(), result.removedIds());
  }
}
