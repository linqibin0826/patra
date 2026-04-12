package com.patra.catalog.app.usecase.venue.query.dto;

import cn.hutool.core.lang.Assert;
import java.util.List;

/// 期刊对比查询参数。
///
/// @param ids 对比期刊 ID 列表（2~5 个）
public record VenueCompareQuery(List<Long> ids) {

  /// 构造期刊对比查询参数并执行校验。
  public VenueCompareQuery {
    Assert.notEmpty(ids, "对比期刊 ID 列表不能为空");
    Assert.isTrue(ids.size() >= 2, "至少需要 2 本期刊进行对比");
    Assert.isTrue(ids.size() <= 5, "最多对比 5 本期刊");
    ids = List.copyOf(ids);
  }

  /// 创建期刊对比查询参数。
  ///
  /// @param ids 对比期刊 ID 列表
  /// @return 期刊对比查询参数
  public static VenueCompareQuery of(List<Long> ids) {
    return new VenueCompareQuery(ids);
  }
}
