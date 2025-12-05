package com.patra.catalog.domain.model.entity;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.patra.catalog.domain.model.enums.RatingSystem;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;

/// 载体评级实体（独立实体，非聚合成员）。
///
/// 设计说明：
///
/// - 支持多种评价体系（JCR、中科院分区、Scopus CiteScore）
/// - 每个 Venue 每年每种评价体系一条记录
/// - 通用字段（quartile、impactScore）支持跨体系查询
/// - 各体系特有数据存储在 JSON 字段中
///
/// 业务规则：
///
/// - venue_id、year、rating_system 必填
/// - 同一 Venue + 年份 + 评价体系唯一（uk_venue_year_system）
/// - 年份范围：2000-2100
/// - impactScore 精度：DECIMAL(10,4)
///
/// 各评价体系 rating_data JSON 结构：
///
/// **JCR**:
/// ```json
/// {
///   "jif": 42.778,                    // Journal Impact Factor
///   "jif_without_self_cites": 41.234, // 排除自引的 JIF
///   "jci": 5.12,                       // Journal Citation Indicator
///   "eigenfactor": 0.45678,            // Eigenfactor Score
///   "article_influence": 15.234,       // Article Influence Score
///   "immediacy_index": 8.456,          // Immediacy Index
///   "total_cites": 125000              // 总被引次数
/// }
/// ```
///
/// **CAS（中科院分区）**:
/// ```json
/// {
///   "partition": "1区",      // 分区：1-4区
///   "is_top": true,           // 是否 Top 期刊
///   "trend": "up",            // 升降趋势：up/down/stable
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
/// 使用示例：
///
/// ```java
/// // 创建 JCR 评级记录
/// VenueRating rating = VenueRating.create(
///     123L,                    // venueId
///     2024,                    // year
///     RatingSystem.JCR,        // ratingSystem
///     "Q1",                    // quartile
///     new BigDecimal("42.778") // impactScore (JIF)
/// );
///
/// // 设置评级详情 JSON
/// rating.withRatingData(jcrJsonData);
/// ```
///
/// @author linqibin
/// @since 0.1.0
@Getter
public class VenueRating implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 最小年份
  private static final int MIN_YEAR = 2000;

  /// 最大年份
  private static final int MAX_YEAR = 2100;

  // ========== 标识符 ==========

  /// 主键 ID（由 Repository 在持久化时分配）
  private Long id;

  /// 关联的 Venue ID（必填）
  private final Long venueId;

  // ========== 评级信息 ==========

  /// 评级年份（必填，2000-2100）
  private final int year;

  /// 评价体系（必填）
  private final RatingSystem ratingSystem;

  // ========== 通用冗余字段（高频查询优化） ==========

  /// 分区（Q1-Q4 或 1区-4区）
  private String quartile;

  /// 影响力分数（JIF / CiteScore / 复合IF）
  private BigDecimal impactScore;

  // ========== 各体系特有数据 ==========

  /// 评级详情（JSON，各体系特有字段）
  private String ratingData;

  /// 学科分类及分区（JSON）
  private String categories;

  // ========== 数据来源 ==========

  /// 数据来源 URL
  private String sourceUrl;

  /// 数据获取时间
  private Instant fetchedAt;

  /// 私有构造函数。
  ///
  /// @param id 主键 ID（新建时为 null）
  /// @param venueId 关联的 Venue ID
  /// @param year 评级年份
  /// @param ratingSystem 评价体系
  private VenueRating(Long id, Long venueId, int year, RatingSystem ratingSystem) {
    Assert.notNull(venueId, "Venue ID 不能为空");
    Assert.isTrue(
        year >= MIN_YEAR && year <= MAX_YEAR, "年份必须在 {}-{} 之间：{}", MIN_YEAR, MAX_YEAR, year);
    Assert.notNull(ratingSystem, "评价体系不能为空");

    this.id = id;
    this.venueId = venueId;
    this.year = year;
    this.ratingSystem = ratingSystem;
  }

  // ========== 工厂方法 ==========

  /// 创建评级记录（含分区和影响力分数）。
  ///
  /// @param venueId 关联的 Venue ID
  /// @param year 评级年份
  /// @param ratingSystem 评价体系
  /// @param quartile 分区
  /// @param impactScore 影响力分数
  /// @return 评级实体
  public static VenueRating create(
      Long venueId, int year, RatingSystem ratingSystem, String quartile, BigDecimal impactScore) {
    VenueRating rating = new VenueRating(null, venueId, year, ratingSystem);
    rating.quartile = quartile;
    rating.impactScore = impactScore;
    rating.fetchedAt = Instant.now();
    return rating;
  }

  /// 创建评级记录（仅必填字段）。
  ///
  /// @param venueId 关联的 Venue ID
  /// @param year 评级年份
  /// @param ratingSystem 评价体系
  /// @return 评级实体
  public static VenueRating create(Long venueId, int year, RatingSystem ratingSystem) {
    VenueRating rating = new VenueRating(null, venueId, year, ratingSystem);
    rating.fetchedAt = Instant.now();
    return rating;
  }

  /// 从持久化状态重建实体（由 Repository 使用）。
  ///
  /// @param id 主键 ID
  /// @param venueId 关联的 Venue ID
  /// @param year 评级年份
  /// @param ratingSystem 评价体系
  /// @return 重建的实体
  public static VenueRating restore(Long id, Long venueId, int year, RatingSystem ratingSystem) {
    return new VenueRating(id, venueId, year, ratingSystem);
  }

  // ========== 设置方法（链式调用） ==========

  /// 设置 ID（由 Repository 在持久化后回写）。
  ///
  /// @param id 主键 ID
  public void assignId(Long id) {
    this.id = id;
  }

  /// 设置分区。
  ///
  /// @param quartile 分区（Q1-Q4 或 1区-4区）
  /// @return 当前对象
  public VenueRating withQuartile(String quartile) {
    this.quartile = quartile;
    return this;
  }

  /// 设置影响力分数。
  ///
  /// @param impactScore 影响力分数
  /// @return 当前对象
  public VenueRating withImpactScore(BigDecimal impactScore) {
    this.impactScore = impactScore;
    return this;
  }

  /// 设置评级详情 JSON。
  ///
  /// @param ratingData 评级详情 JSON
  /// @return 当前对象
  public VenueRating withRatingData(String ratingData) {
    this.ratingData = ratingData;
    return this;
  }

  /// 设置学科分类 JSON。
  ///
  /// @param categories 学科分类 JSON
  /// @return 当前对象
  public VenueRating withCategories(String categories) {
    this.categories = categories;
    return this;
  }

  /// 设置数据来源 URL。
  ///
  /// @param sourceUrl 来源 URL
  /// @return 当前对象
  public VenueRating withSourceUrl(String sourceUrl) {
    this.sourceUrl = sourceUrl;
    return this;
  }

  /// 设置数据获取时间。
  ///
  /// @param fetchedAt 获取时间
  /// @return 当前对象
  public VenueRating withFetchedAt(Instant fetchedAt) {
    this.fetchedAt = fetchedAt;
    return this;
  }

  // ========== 业务方法 ==========

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

  /// 更新评级数据。
  ///
  /// @param quartile 分区
  /// @param impactScore 影响力分数
  /// @param ratingData 评级详情 JSON
  public void updateRating(String quartile, BigDecimal impactScore, String ratingData) {
    this.quartile = quartile;
    this.impactScore = impactScore;
    this.ratingData = ratingData;
    this.fetchedAt = Instant.now();
  }

  @Override
  public String toString() {
    return String.format(
        "VenueRating[venueId=%d, year=%d, system=%s, quartile=%s, score=%s]",
        venueId, year, ratingSystem.getCode(), quartile, impactScore);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof VenueRating that)) {
      return false;
    }
    // 业务相等性：venueId + year + ratingSystem
    return year == that.year
        && Objects.equals(venueId, that.venueId)
        && ratingSystem == that.ratingSystem;
  }

  @Override
  public int hashCode() {
    return Objects.hash(venueId, year, ratingSystem);
  }
}
