package dev.linqibin.patra.catalog.app.usecase.venue.query.dto;

import cn.hutool.core.lang.Assert;

/// Venue 实例列表查询参数。
///
/// @param venueId 期刊主键 ID（必填）
/// @param year 出版年份过滤（可空）
/// @param page 页码（可空，由应用层归一化）
/// @param pageSize 每页大小（可空，由应用层归一化）
public record VenueInstanceListQuery(Long venueId, Integer year, Integer page, Integer pageSize) {

  /// 构造并验证查询参数。
  public VenueInstanceListQuery {
    Assert.notNull(venueId, "Venue ID 不能为空");
  }

  /// 创建 Venue 实例列表查询参数。
  ///
  /// @param venueId 期刊主键 ID
  /// @param year 出版年份过滤（可空）
  /// @param page 页码（可空）
  /// @param pageSize 每页大小（可空）
  /// @return Venue 实例列表查询参数
  public static VenueInstanceListQuery of(
      Long venueId, Integer year, Integer page, Integer pageSize) {
    return new VenueInstanceListQuery(venueId, year, page, pageSize);
  }
}
