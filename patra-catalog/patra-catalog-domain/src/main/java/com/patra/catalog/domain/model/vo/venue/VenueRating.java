package com.patra.catalog.domain.model.vo.venue;

import cn.hutool.core.util.StrUtil;
import com.patra.catalog.domain.model.enums.RatingSystem;
import java.math.BigDecimal;
import java.time.Instant;

/// 载体评级值对象。
///
/// 表示某个 Venue 在特定年份、特定评价体系下的评级信息。
///
/// **设计说明**：
///
/// - 值对象：身份由 `(venueId, year, ratingSystem)` 决定，无独立生命周期
/// - 不可变：所有字段在创建时确定，之后不可修改
/// - 支持多种评价体系（JCR、中科院分区、Scopus CiteScore）
/// - 通用字段（quartile、impactScore）支持跨体系查询
/// - 各体系特有数据存储在 JSON 字段中
///
/// **业务规则**：
///
/// - venueId、year、ratingSystem 必填
/// - 年份范围：2000-2100
/// - impactScore 精度：DECIMAL(10,4)
///
/// **各评价体系 ratingData JSON 结构**：
///
/// **JCR**:
/// ```json
/// {
///   "jif": 42.778,
///   "jif_without_self_cites": 41.234,
///   "jci": 5.12,
///   "eigenfactor": 0.45678,
///   "article_influence": 15.234,
///   "immediacy_index": 8.456,
///   "total_cites": 125000
/// }
/// ```
///
/// **CAS（中科院分区）**:
/// ```json
/// {
///   "partition": "1区",
///   "is_top": true,
///   "trend": "up",
///   "comprehensive_if": 45.678,
///   "journal_if": 42.778
/// }
/// ```
///
/// **SCOPUS**:
/// ```json
/// {
///   "cite_score": 12.5,
///   "cite_score_tracker": 13.2,
///   "snip": 2.345,
///   "sjr": 5.678,
///   "percentile": 98
/// }
/// ```
///
/// @param venueId 关联的 Venue ID（必填）
/// @param year 评级年份（必填，2000-2100）
/// @param ratingSystem 评价体系（必填）
/// @param quartile 分区（Q1-Q4 或 1区-4区）
/// @param impactScore 影响力分数（JIF / CiteScore / 复合IF）
/// @param ratingData 评级详情（JSON，各体系特有字段）
/// @param categories 学科分类及分区（JSON）
/// @param sourceUrl 数据来源 URL
/// @param fetchedAt 数据获取时间
/// @author linqibin
/// @since 0.1.0
public record VenueRating(
    Long venueId,
    int year,
    RatingSystem ratingSystem,
    String quartile,
    BigDecimal impactScore,
    String ratingData,
    String categories,
    String sourceUrl,
    Instant fetchedAt) {

  /// 最小年份
  private static final int MIN_YEAR = 2000;

  /// 最大年份
  private static final int MAX_YEAR = 2100;

  /// 紧凑构造函数，用于参数验证。
  public VenueRating {
    if (venueId == null) {
      throw new IllegalArgumentException("Venue ID 不能为空");
    }
    if (year < MIN_YEAR || year > MAX_YEAR) {
      throw new IllegalArgumentException(
          String.format("年份必须在 %d-%d 之间：%d", MIN_YEAR, MAX_YEAR, year));
    }
    if (ratingSystem == null) {
      throw new IllegalArgumentException("评价体系不能为空");
    }
  }

  // ========== 工厂方法 ==========

  /// 创建评级记录（含分区和影响力分数）。
  ///
  /// @param venueId 关联的 Venue ID
  /// @param year 评级年份
  /// @param ratingSystem 评价体系
  /// @param quartile 分区
  /// @param impactScore 影响力分数
  /// @return 评级值对象
  public static VenueRating create(
      Long venueId, int year, RatingSystem ratingSystem, String quartile, BigDecimal impactScore) {
    return new VenueRating(
        venueId, year, ratingSystem, quartile, impactScore, null, null, null, Instant.now());
  }

  /// 创建评级记录（仅必填字段）。
  ///
  /// @param venueId 关联的 Venue ID
  /// @param year 评级年份
  /// @param ratingSystem 评价体系
  /// @return 评级值对象
  public static VenueRating create(Long venueId, int year, RatingSystem ratingSystem) {
    return new VenueRating(
        venueId, year, ratingSystem, null, null, null, null, null, Instant.now());
  }

  /// 创建完整的评级记录。
  ///
  /// @param venueId 关联的 Venue ID
  /// @param year 评级年份
  /// @param ratingSystem 评价体系
  /// @param quartile 分区
  /// @param impactScore 影响力分数
  /// @param ratingData 评级详情 JSON
  /// @param categories 学科分类 JSON
  /// @param sourceUrl 来源 URL
  /// @param fetchedAt 获取时间
  /// @return 评级值对象
  public static VenueRating of(
      Long venueId,
      int year,
      RatingSystem ratingSystem,
      String quartile,
      BigDecimal impactScore,
      String ratingData,
      String categories,
      String sourceUrl,
      Instant fetchedAt) {
    return new VenueRating(
        venueId,
        year,
        ratingSystem,
        quartile,
        impactScore,
        ratingData,
        categories,
        sourceUrl,
        fetchedAt != null ? fetchedAt : Instant.now());
  }

  // ========== 查询方法 ==========

  /// 判断是否有分区信息。
  ///
  /// @return true 如果有分区
  public boolean hasQuartile() {
    return StrUtil.isNotBlank(quartile);
  }

  /// 判断是否有影响力分数。
  ///
  /// @return true 如果有影响力分数
  public boolean hasImpactScore() {
    return impactScore != null;
  }

  /// 判断是否有评级详情。
  ///
  /// @return true 如果有评级详情
  public boolean hasRatingData() {
    return StrUtil.isNotBlank(ratingData);
  }

  /// 判断是否有学科分类。
  ///
  /// @return true 如果有学科分类
  public boolean hasCategories() {
    return StrUtil.isNotBlank(categories);
  }

  /// 判断是否为 JCR 评级。
  ///
  /// @return true 如果为 JCR
  public boolean isJcrRating() {
    return ratingSystem.isJcr();
  }

  /// 判断是否为中科院分区评级。
  ///
  /// @return true 如果为中科院分区
  public boolean isCasRating() {
    return ratingSystem.isCas();
  }

  /// 判断是否为 Scopus 评级。
  ///
  /// @return true 如果为 Scopus
  public boolean isScopusRating() {
    return ratingSystem.isScopus();
  }

  /// 判断是否为顶级分区（Q1 或 1区）。
  ///
  /// @return true 如果为顶级分区
  public boolean isTopQuartile() {
    if (StrUtil.isBlank(quartile)) {
      return false;
    }
    String normalized = quartile.trim().toUpperCase();
    return "Q1".equals(normalized) || "1区".equals(quartile) || "1".equals(normalized);
  }

  /// 标准化分区值（统一为 Q1-Q4 格式）。
  ///
  /// @return 标准化后的分区，如果无法标准化则返回原值
  public String getNormalizedQuartile() {
    if (StrUtil.isBlank(quartile)) {
      return null;
    }

    String normalized = quartile.trim();

    // 处理中科院分区格式（1区-4区）
    if (normalized.endsWith("区")) {
      String num = normalized.substring(0, normalized.length() - 1);
      if (num.matches("[1-4]")) {
        return "Q" + num;
      }
    }

    // 处理纯数字格式
    if (normalized.matches("[1-4]")) {
      return "Q" + normalized;
    }

    // 已经是 Q1-Q4 格式
    if (normalized.toUpperCase().matches("Q[1-4]")) {
      return normalized.toUpperCase();
    }

    return quartile;
  }
}
