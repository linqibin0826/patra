package dev.patra.interaction.app.discussion.usecase.query;

import java.util.List;

/// 讨论列表的一页。
///
/// @param items 楼
/// @param nextCursor 下一页的游标；没有下一页为 `null`
public record ThreadPageView(List<ThreadView> items, String nextCursor) {

  /// 防御性拷贝。
  public ThreadPageView {
    items = List.copyOf(items);
  }

  /// 创建。
  ///
  /// @param items 楼
  /// @param nextCursor 游标，可空
  /// @return 一页
  public static ThreadPageView of(List<ThreadView> items, String nextCursor) {
    return new ThreadPageView(items, nextCursor);
  }
}
