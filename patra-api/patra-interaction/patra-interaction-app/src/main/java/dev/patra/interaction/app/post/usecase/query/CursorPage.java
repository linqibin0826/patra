package dev.patra.interaction.app.post.usecase.query;

import java.util.List;

/// 游标分页的一页。
///
/// @param items 本页
/// @param nextCursor 下一页的游标（本页最后一条的 ID）；没有下一页为 `null`
/// @param <T> 条目类型
public record CursorPage<T>(List<T> items, String nextCursor) {

  /// 防御性拷贝。
  public CursorPage {
    items = List.copyOf(items);
  }

  /// 创建。
  ///
  /// @param items 本页
  /// @param nextCursor 下一页的游标，可空
  /// @param <T> 条目类型
  /// @return 一页
  public static <T> CursorPage<T> of(List<T> items, String nextCursor) {
    return new CursorPage<>(items, nextCursor);
  }

  /// 空页。
  ///
  /// @param <T> 条目类型
  /// @return 空页
  public static <T> CursorPage<T> empty() {
    return new CursorPage<>(List.of(), null);
  }
}
