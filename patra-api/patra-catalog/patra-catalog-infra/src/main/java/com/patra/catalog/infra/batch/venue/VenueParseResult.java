package com.patra.catalog.infra.batch.venue;

import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import com.patra.catalog.domain.model.vo.venue.VenuePublicationStats;
import java.util.List;

/// OpenAlex Source 解析结果。
///
/// **职责**：
///
/// 封装解析后的 VenueAggregate 及其关联的年度指标数据。
///
/// **DDD 嵌入式值对象设计**：
///
/// 聚合根（VenueAggregate）已包含所有嵌入式值对象：
/// - `publicationProfile` - 出版概况
/// - `citationMetrics` - 引用指标
/// - `openAccess` - 开放获取信息
/// - `affiliatedSocieties` - 关联学会
///
/// 这些数据作为 JSON 字段随聚合根一起持久化到 `cat_venue` 表。
///
/// **独立存储的数据**：
///
/// | 字段 | 说明 | 存储目标 |
/// |------|------|----------|
/// | aggregate | 聚合根（含嵌入式值对象） | cat_venue + cat_venue_identifier |
/// | yearlyMetrics | 年度指标（1:N 时序数据） | cat_venue_publication_stats |
///
/// **使用场景**：
///
/// - `OpenAlexSourceParser` 返回此类型
/// - `VenueInitializeItemReader` 传递此类型给 ItemWriter
/// - Repository 保存聚合根（含嵌入式 JSON）和年度指标
///
/// @param aggregate 解析后的 VenueAggregate 聚合根（必填，含嵌入式值对象）
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
