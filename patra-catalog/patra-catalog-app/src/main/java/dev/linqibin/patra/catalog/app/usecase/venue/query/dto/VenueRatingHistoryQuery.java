package dev.linqibin.patra.catalog.app.usecase.venue.query.dto;

import cn.hutool.core.lang.Assert;

/// Venue 评级历史查询参数。
///
/// @param id 期刊主键 ID
public record VenueRatingHistoryQuery(Long id) {

  /// 构造 Venue 评级历史查询参数并执行校验。
  public VenueRatingHistoryQuery {
    Assert.notNull(id, "期刊 ID 不能为空");
  }

  /// 创建 Venue 评级历史查询参数。
  ///
  /// @param id 期刊主键 ID
  /// @return Venue 评级历史查询参数
  public static VenueRatingHistoryQuery of(Long id) {
    return new VenueRatingHistoryQuery(id);
  }
}
