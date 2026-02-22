package com.patra.catalog.domain.model.vo.venue;

import java.util.List;

/// OpenAlex 富化数据值对象。
///
/// 封装从 OpenAlex Sources API 查询获取的期刊引用指标、年度统计和开放获取数据，
/// 作为批量查询的聚合返回结果。
///
/// **包含的数据**：
///
/// | 字段 | OpenAlex 来源 | 说明 |
/// |------|--------------|------|
/// | openAlexId | `id` (stripped) | OpenAlex Source ID（如 "S137773608"） |
/// | citationMetrics | `works_count` + `cited_by_count` + `summary_stats` | 引用指标快照 |
/// | yearlyStats | `counts_by_year` | 年度发文统计时序数据 |
/// | openAccessInfo | `is_oa` + `is_in_doaj` + `apc_usd` + `apc_prices` | 开放获取信息 |
///
/// **不变性**：Record 自动保证不可变。yearlyStats 在紧凑构造器中进行防御性拷贝。
///
/// **null 语义**：`citationMetrics` 和 `openAccessInfo` 为 null 表示 OpenAlex 中无对应数据，
/// 不代表查询失败。`yearlyStats` 为 null 时自动转为空列表。
///
/// @param openAlexId OpenAlex Source ID（如 "S137773608"）
/// @param citationMetrics 引用指标快照（可为 null）
/// @param yearlyStats 年度发文统计列表（不可变）
/// @param openAccessInfo 开放获取信息（可为 null）
/// @author linqibin
/// @since 0.1.0
public record VenueOpenAlexEnrichment(
    String openAlexId,
    CitationMetrics citationMetrics,
    List<VenuePublicationStats> yearlyStats,
    OpenAccessInfo openAccessInfo) {

  /// 紧凑构造器：yearlyStats 防御性拷贝。
  public VenueOpenAlexEnrichment {
    yearlyStats = yearlyStats != null ? List.copyOf(yearlyStats) : List.of();
  }

  /// 创建 OpenAlex 富化数据。
  ///
  /// @param openAlexId OpenAlex Source ID
  /// @param citationMetrics 引用指标快照（可为 null）
  /// @param yearlyStats 年度发文统计列表（可为 null）
  /// @param openAccessInfo 开放获取信息（可为 null）
  /// @return 富化数据值对象
  public static VenueOpenAlexEnrichment of(
      String openAlexId,
      CitationMetrics citationMetrics,
      List<VenuePublicationStats> yearlyStats,
      OpenAccessInfo openAccessInfo) {
    return new VenueOpenAlexEnrichment(openAlexId, citationMetrics, yearlyStats, openAccessInfo);
  }

  /// 判断是否有引用指标。
  ///
  /// @return true 如果有引用指标
  public boolean hasCitationMetrics() {
    return citationMetrics != null;
  }

  /// 判断是否有年度统计数据。
  ///
  /// @return true 如果有年度统计
  public boolean hasYearlyStats() {
    return !yearlyStats.isEmpty();
  }

  /// 判断是否有开放获取信息。
  ///
  /// @return true 如果有开放获取信息
  public boolean hasOpenAccessInfo() {
    return openAccessInfo != null;
  }
}
