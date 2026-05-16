package dev.linqibin.patra.catalog.app.usecase.venue.query.dto;

import cn.hutool.core.lang.Assert;

/// Venue 详情查询参数。
///
/// @param id 期刊主键 ID
public record VenueDetailQuery(Long id) {

  /// 构造 Venue 详情查询参数并执行校验。
  public VenueDetailQuery {
    Assert.notNull(id, "期刊 ID 不能为空");
  }

  /// 创建 Venue 详情查询参数。
  ///
  /// @param id 期刊主键 ID
  /// @return Venue 详情查询参数
  public static VenueDetailQuery of(Long id) {
    return new VenueDetailQuery(id);
  }
}
