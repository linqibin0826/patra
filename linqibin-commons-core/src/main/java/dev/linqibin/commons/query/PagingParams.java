package dev.linqibin.commons.query;

import cn.hutool.core.lang.Assert;

/// 已验证的分页请求值对象。
///
/// 封装经过校验的分页参数，保证 `page >= 1` 且 `pageSize >= 1`。
/// 对于来自外部的不可信输入，使用 [normalize] 归一化工厂统一处理 null、非法值和超限值。
///
/// @param page 页码（从 1 开始）
/// @param pageSize 每页大小
/// @since 0.1.0
public record PagingParams(int page, int pageSize) {

  /// 默认每页大小。
  public static final int DEFAULT_PAGE_SIZE = 20;

  /// 每页大小上限。
  public static final int MAX_PAGE_SIZE = 100;

  /// 构造并验证分页参数。
  public PagingParams {
    Assert.isTrue(page >= 1, "页码必须大于等于 1");
    Assert.isTrue(pageSize >= 1, "每页大小必须大于等于 1");
  }

  /// 创建分页参数。
  ///
  /// @param page 页码（从 1 开始）
  /// @param pageSize 每页大小
  /// @return 已验证的分页参数
  public static PagingParams of(int page, int pageSize) {
    return new PagingParams(page, pageSize);
  }

  /// 使用全局默认值归一化外部输入。
  ///
  /// 等价于 `normalize(page, pageSize, DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE)`。
  ///
  /// @param page 原始页码（可空）
  /// @param pageSize 原始每页大小（可空）
  /// @return 归一化后的分页参数
  public static PagingParams normalize(Integer page, Integer pageSize) {
    return normalize(page, pageSize, DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE);
  }

  /// 归一化外部输入为合法分页参数。
  ///
  /// - `page` 为 null 或 < 1 时回退到 1
  /// - `pageSize` 为 null 或 < 1 时回退到 `defaultSize`
  /// - `pageSize` 超过 `maxSize` 时截断为 `maxSize`
  ///
  /// @param page 原始页码（可空）
  /// @param pageSize 原始每页大小（可空）
  /// @param defaultSize 默认每页大小
  /// @param maxSize 每页大小上限
  /// @return 归一化后的分页参数
  public static PagingParams normalize(
      Integer page, Integer pageSize, int defaultSize, int maxSize) {
    int normalizedPage = (page == null || page < 1) ? 1 : page;
    int normalizedSize = (pageSize == null || pageSize < 1) ? defaultSize : pageSize;
    if (normalizedSize > maxSize) {
      normalizedSize = maxSize;
    }
    return new PagingParams(normalizedPage, normalizedSize);
  }
}
