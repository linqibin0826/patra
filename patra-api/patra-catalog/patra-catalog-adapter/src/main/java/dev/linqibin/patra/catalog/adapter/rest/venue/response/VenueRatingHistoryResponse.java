package dev.linqibin.patra.catalog.adapter.rest.venue.response;

import java.math.BigDecimal;
import java.util.List;

/// 期刊评级历史响应。
///
/// 包含 JCR/CAS/Scopus/预警的完整历史记录，供前端趋势图展示。
///
/// @param jcr JCR 评级历史列表（按年份降序）
/// @param cas CAS 分区历史列表（按年份降序）
/// @param scopus Scopus 指标历史列表（按年份降序）
/// @param warnings CAS 预警历史列表（按发布年份降序）
public record VenueRatingHistoryResponse(
    List<JcrRatingDto> jcr,
    List<CasRatingDto> cas,
    List<ScopusRatingDto> scopus,
    List<WarningDto> warnings) {

  /// JCR 年度评级 DTO。
  ///
  /// @param year 评级年份
  /// @param impactFactor JIF 影响因子
  /// @param jifQuartile JIF 分区（Q1-Q4）
  /// @param jifRank JIF 排名（如 "2/136"）
  /// @param jifPercentile JIF 学科百分位
  /// @param selfCitationRate 自引率
  /// @param collection 收录集（SCIE/SSCI/AHCI）
  public record JcrRatingDto(
      short year,
      BigDecimal impactFactor,
      String jifQuartile,
      String jifRank,
      BigDecimal jifPercentile,
      BigDecimal selfCitationRate,
      String collection) {}

  /// CAS 中科院分区年度 DTO。
  ///
  /// @param year 分区年份
  /// @param edition CAS 版本名称
  /// @param majorCategory 大类学科
  /// @param majorQuartile 大类分区
  /// @param minorSubject 小类学科
  /// @param minorQuartile 小类分区
  /// @param isTopJournal 是否为 Top 期刊
  /// @param isReviewJournal 是否为综述期刊
  public record CasRatingDto(
      short year,
      String edition,
      String majorCategory,
      String majorQuartile,
      String minorSubject,
      String minorQuartile,
      boolean isTopJournal,
      boolean isReviewJournal) {}

  /// Scopus 年度指标 DTO。
  ///
  /// @param year 指标年份
  /// @param citeScore CiteScore
  /// @param sjr SCImago Journal Rank
  /// @param snip Source Normalized Impact per Paper
  /// @param quartile CiteScore 分区（Q1-Q4）
  /// @param percentile 学科内百分位排名
  /// @param documentCount 该年发文量
  /// @param citationCount 该年被引次数
  public record ScopusRatingDto(
      short year,
      BigDecimal citeScore,
      BigDecimal sjr,
      BigDecimal snip,
      String quartile,
      BigDecimal percentile,
      Integer documentCount,
      Integer citationCount) {}

  /// CAS 预警名单历史 DTO。
  ///
  /// @param publishedYear 预警名单发布年份
  /// @param editionLabel 原始版本标签
  /// @param inWarningList 是否在预警名单中
  /// @param warningLevel 预警级别代码（high/medium/low，可为 null）
  public record WarningDto(
      short publishedYear, String editionLabel, boolean inWarningList, String warningLevel) {}
}
