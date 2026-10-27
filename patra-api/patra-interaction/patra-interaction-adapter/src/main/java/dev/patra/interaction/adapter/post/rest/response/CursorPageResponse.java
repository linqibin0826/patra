package dev.patra.interaction.adapter.post.rest.response;

import dev.patra.interaction.app.post.usecase.query.CursorPage;
import java.util.List;
import java.util.function.Function;

/// 游标分页的一页。`nextCursor` 原样传回下一次请求的 `cursor`；为 `null` 时没有下一页。
///
/// @param items 本页
/// @param nextCursor 下一页的游标，可空
/// @param <T> 条目类型
public record CursorPageResponse<T>(List<T> items, String nextCursor) {

  /// 防御性拷贝。
  public CursorPageResponse {
    items = List.copyOf(items);
  }

  /// 转换。
  ///
  /// @param page 一页
  /// @param mapper 条目转换
  /// @param <S> 查询条目类型
  /// @param <T> 响应条目类型
  /// @return 响应
  public static <S, T> CursorPageResponse<T> from(CursorPage<S> page, Function<S, T> mapper) {
    return new CursorPageResponse<>(page.items().stream().map(mapper).toList(), page.nextCursor());
  }
}
