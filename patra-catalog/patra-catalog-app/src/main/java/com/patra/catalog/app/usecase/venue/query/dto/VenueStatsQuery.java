package com.patra.catalog.app.usecase.venue.query.dto;

import cn.hutool.core.lang.Assert;

/// Venue 发文统计查询参数。
///
/// @param id 期刊主键 ID
public record VenueStatsQuery(Long id) {

  /// 构造 Venue 发文统计查询参数并执行校验。
  public VenueStatsQuery {
    Assert.notNull(id, "期刊 ID 不能为空");
  }

  /// 创建 Venue 发文统计查询参数。
  ///
  /// @param id 期刊主键 ID
  /// @return Venue 发文统计查询参数
  public static VenueStatsQuery of(Long id) {
    return new VenueStatsQuery(id);
  }
}
