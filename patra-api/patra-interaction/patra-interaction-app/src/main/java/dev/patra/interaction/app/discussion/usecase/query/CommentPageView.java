package dev.patra.interaction.app.discussion.usecase.query;

import java.util.List;

/// 楼内回复的一页。
///
/// @param items 回复（正序）
/// @param nextCursor 下一页的游标；没有下一页为 `null`
public record CommentPageView(List<CommentView> items, String nextCursor) {

  /// 防御性拷贝。
  public CommentPageView {
    items = List.copyOf(items);
  }

  /// 创建。
  ///
  /// @param items 回复
  /// @param nextCursor 游标，可空
  /// @return 一页
  public static CommentPageView of(List<CommentView> items, String nextCursor) {
    return new CommentPageView(items, nextCursor);
  }
}
