package dev.patra.interaction.app.discussion.usecase.comment;

import java.util.List;

/// 删除的结果：门户据此把这条换成占位，或者移除这条与级联去掉的占位（设计简报 §3.9「界面只按返回显示」）。
///
/// @param placeholder 留下了占位（有回复，或者本来就是占位）
/// @param removedIds 整条去掉的评论：这条在前，之后是级联去掉的占位祖先；留占位时为空
public record DeleteResult(boolean placeholder, List<Long> removedIds) {

  /// 防御性拷贝。
  public DeleteResult {
    removedIds = List.copyOf(removedIds);
  }

  /// 创建。
  ///
  /// @param placeholder 是否留下占位
  /// @param removedIds 去掉的评论
  /// @return 结果
  public static DeleteResult of(boolean placeholder, List<Long> removedIds) {
    return new DeleteResult(placeholder, removedIds);
  }
}
