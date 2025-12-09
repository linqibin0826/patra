package com.patra.catalog.infra.batch.venue;

import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import com.patra.catalog.domain.model.vo.venue.VenuePublicationStats;
import java.util.List;

/// OpenAlex Source 解析结果。
///
/// **职责**：
///
/// 封装解析后的 VenueAggregate 及其关联的年度指标数据。
/// 由于年度指标已从聚合根移出，需要通过此记录一并传递。
///
/// **使用场景**：
///
/// - `OpenAlexSourceParser` 返回此类型
/// - `VenueInitializeItemReader` 传递此类型给 ItemWriter
/// - Application 层分别处理聚合根和年度指标
///
/// @param aggregate 解析后的 VenueAggregate 聚合根
/// @param yearlyMetrics 关联的年度指标列表（可为空列表）
/// @author linqibin
/// @since 0.1.0
public record VenueParseResult(
    VenueAggregate aggregate, List<VenuePublicationStats> yearlyMetrics) {

  /// 创建解析结果。
  ///
  /// @param aggregate 聚合根（不能为 null）
  /// @param yearlyMetrics 年度指标（null 会被转换为空列表）
  public VenueParseResult {
    if (aggregate == null) {
      throw new IllegalArgumentException("aggregate 不能为 null");
    }
    if (yearlyMetrics == null) {
      yearlyMetrics = List.of();
    }
  }

  /// 是否有年度指标数据。
  ///
  /// @return true 如果存在年度指标
  public boolean hasYearlyMetrics() {
    return !yearlyMetrics.isEmpty();
  }
}
