package com.patra.common.query;

import cn.hutool.core.lang.Assert;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/// 泛型分页响应容器。
///
/// 封装分页查询结果，包括分页元数据和数据列表。
/// 支持通过 [map] 方法在跨层传递时进行类型转换，保留分页元数据不变。
///
/// @param page 页码（从 1 开始）
/// @param pageSize 每页大小
/// @param total 总记录数
/// @param totalPages 总页数
/// @param items 当前页数据列表
/// @param <T> 数据项类型
/// @since 0.1.0
public record PageResult<T>(int page, int pageSize, long total, int totalPages, List<T> items) {

  /// 构造并验证分页结果。
  public PageResult {
    Assert.isTrue(page >= 1, "页码必须大于等于 1");
    Assert.isTrue(pageSize >= 1, "每页大小必须大于等于 1");
    Assert.isTrue(total >= 0, "总记录数不能为负数");
    Assert.isTrue(totalPages >= 0, "总页数不能为负数");
    items = List.copyOf(Objects.requireNonNull(items, "分页数据项不能为空"));
  }

  /// 创建分页结果，自动计算 totalPages。
  ///
  /// @param items 当前页数据列表
  /// @param page 页码（从 1 开始）
  /// @param pageSize 每页大小
  /// @param total 总记录数
  /// @param <T> 数据项类型
  /// @return 分页结果
  public static <T> PageResult<T> of(List<T> items, int page, int pageSize, long total) {
    int totalPages = (total == 0) ? 0 : (int) Math.ceil((double) total / pageSize);
    return new PageResult<>(page, pageSize, total, totalPages, items);
  }

  /// 创建空分页结果。
  ///
  /// @param page 页码
  /// @param pageSize 每页大小
  /// @param <T> 数据项类型
  /// @return 空分页结果
  public static <T> PageResult<T> empty(int page, int pageSize) {
    return new PageResult<>(page, pageSize, 0, 0, List.of());
  }

  /// 转换数据项类型，保留分页元数据不变。
  ///
  /// @param mapper 转换函数
  /// @param <U> 目标类型
  /// @return 转换后的分页结果
  public <U> PageResult<U> map(Function<T, U> mapper) {
    List<U> mappedItems = items.stream().map(mapper).toList();
    return new PageResult<>(page, pageSize, total, totalPages, mappedItems);
  }
}
