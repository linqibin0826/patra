package dev.patra.interaction.adapter.collection.rest.response;

import dev.patra.interaction.app.collection.usecase.query.PageView;
import java.util.List;
import java.util.function.Function;

/// 分页响应（与 catalog 门户分页同形）。
///
/// @param page 页码，从 1 开始
/// @param pageSize 每页条数
/// @param total 总条数
/// @param totalPages 总页数
/// @param items 本页
/// @param <T> 条目类型
public record PageResponse<T>(int page, int pageSize, long total, int totalPages, List<T> items) {

  /// 防御性拷贝。
  public PageResponse {
    items = List.copyOf(items);
  }

  /// 从查询结果转换。
  ///
  /// @param page 查询结果
  /// @param mapper 条目转换
  /// @param <S> 查询条目类型
  /// @param <T> 响应条目类型
  /// @return 响应
  public static <S, T> PageResponse<T> from(PageView<S> page, Function<S, T> mapper) {
    return new PageResponse<>(
        page.page(),
        page.pageSize(),
        page.total(),
        page.totalPages(),
        page.items().stream().map(mapper).toList());
  }
}
