package com.patra.catalog.domain.port.enrichment;

import java.util.List;
import lombok.Builder;

/// Scopus Serial Title API 原始数据 DTO。
///
/// 端口级中间数据结构，封装从 Elsevier Scopus Serial Title API 获取的期刊指标字段。
/// 由 {@link ScopusEnrichmentPort} 返回，由下游 Mapper 拆解为
/// `cat_venue_scopus_rating` 评级行。
///
/// **注意**：这不是领域值对象，而是数据源适配层的中间载体。
///
/// **数据分组**：
///
/// | 分组 | 字段 | 说明 |
/// |------|------|------|
/// | 基本信息 | scopusSourceId, title | Scopus 内部标识 |
/// | 当年指标 | citeScore ~ percentCited | 最新年度核心指标 |
/// | 学科分区 | subjectArea ~ percentile | ASJC 学科分类与排名 |
/// | 历史趋势 | yearlyMetrics | 逐年 CiteScore 等指标 |
///
/// **不变性**：Record + `@Builder` 自动保证不可变，`yearlyMetrics` 通过紧凑构造器防御性拷贝。
///
/// @param scopusSourceId Scopus Source ID
/// @param title Scopus 显示的期刊名称
/// @param citeScore 当年 CiteScore
/// @param citeScoreTracker CiteScore Tracker（当年预估值）
/// @param sjr SCImago Journal Rank
/// @param snip Source Normalized Impact per Paper
/// @param documentCount 该年发文量
/// @param citationCount 该年被引次数
/// @param percentCited 被引文献百分比
/// @param subjectArea 主 ASJC 学科领域
/// @param quartile CiteScore 分区（Q1-Q4）
/// @param percentile 学科内百分位排名
/// @param yearlyMetrics 历史年度指标列表（不可变）
/// @author linqibin
/// @since 0.1.0
@Builder
public record ScopusVenueData(
    // 基本信息
    String scopusSourceId,
    String title,
    // 当年指标
    Double citeScore,
    Double citeScoreTracker,
    Double sjr,
    Double snip,
    Integer documentCount,
    Integer citationCount,
    Double percentCited,
    // 学科分区
    String subjectArea,
    String quartile,
    Double percentile,
    // 历史趋势
    List<YearlyMetric> yearlyMetrics) {

  /// 紧凑构造器：对 `yearlyMetrics` 进行防御性拷贝。
  public ScopusVenueData {
    yearlyMetrics = yearlyMetrics != null ? List.copyOf(yearlyMetrics) : List.of();
  }

  /// Scopus 年度指标数据。
  ///
  /// @param year 统计年份
  /// @param citeScore 该年 CiteScore
  /// @param documentCount 该年发文量（可为 null）
  /// @param citationCount 该年被引次数（可为 null）
  /// @param percentCited 被引文献百分比（可为 null）
  @Builder
  public record YearlyMetric(
      int year,
      Double citeScore,
      Integer documentCount,
      Integer citationCount,
      Double percentCited) {}
}
