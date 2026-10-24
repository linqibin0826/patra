package dev.patra.interaction.app.collection.usecase.query;

import java.util.List;

/// 一页（与 catalog 门户分页同形）。
///
/// @param page 页码，从 1 开始
/// @param pageSize 每页条数
/// @param total 总条数
/// @param totalPages 总页数，`total == 0` 时为 0
/// @param items 本页
/// @param <T> 条目类型
public record PageView<T>(int page, int pageSize, long total, int totalPages, List<T> items) {

  /// 防御性拷贝。
  public PageView {
    items = List.copyOf(items);
  }

  /// 创建，自动算总页数。
  ///
  /// @param items 本页
  /// @param page 页码
  /// @param pageSize 每页条数
  /// @param total 总条数
  /// @param <T> 条目类型
  /// @return 一页
  public static <T> PageView<T> of(List<T> items, int page, int pageSize, long total) {
    int totalPages = total == 0 ? 0 : (int) ((total + pageSize - 1) / pageSize);
    return new PageView<>(page, pageSize, total, totalPages, items);
  }
}
