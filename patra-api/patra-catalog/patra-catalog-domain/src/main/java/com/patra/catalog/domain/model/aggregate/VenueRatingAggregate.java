package com.patra.catalog.domain.model.aggregate;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.patra.catalog.domain.model.enums.RatingSystem;
import com.patra.catalog.domain.model.vo.venue.VenueRatingId;
import com.patra.common.domain.AggregateRoot;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;

/// 载体评级聚合根。
///
/// 表示某个 Venue 在特定年份、特定评价体系下的评级信息。
/// 作为独立聚合根，拥有独立的生命周期和一致性边界。
///
/// **设计说明**：
///
/// - 独立聚合根：从 VenueAggregate 的值对象升级而来
/// - 业务唯一键：`(venueId, year, ratingSystem)`，由数据库唯一索引保证
/// - 支持多种评价体系（JCR、中科院分区、Scopus CiteScore）
/// - 通用字段（quartile、impactScore）支持跨体系查询
/// - 各体系特有数据存储在 JSON 字段（ratingData）中
///
/// **不变量**：
///
/// - venueId、year、ratingSystem 一旦创建不可修改
/// - 年份范围：2000-2100
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
/// @author Patra Lin
/// @since 0.6.0
@Getter
public class VenueRatingAggregate extends AggregateRoot<VenueRatingId> {

  /// 最小年份
  private static final int MIN_YEAR = 2000;

  /// 最大年份
  private static final int MAX_YEAR = 2100;

  // ========== 核心属性（不变量） ==========

  /// 关联的 Venue ID（逻辑外键）
  private final Long venueId;

  /// 评级年份（2000-2100）
  private final int year;

  /// 评价体系（JCR/CAS/SCOPUS）
  private final RatingSystem ratingSystem;

  // ========== 可变属性 ==========

  /// 分区（JCR: Q1-Q4，CAS: 1区-4区）
  private String quartile;

  /// 影响力分数（JCR Impact Factor / SCOPUS CiteScore / CAS 复合IF）
  private BigDecimal impactScore;

  /// 评级详情（JSON，各体系特有字段）
  private String ratingData;

  /// 学科分类及分区（JSON）
  private String categories;

  /// 数据来源 URL
  private String sourceUrl;

  /// 数据获取时间
  private Instant fetchedAt;

  // ========== 私有构造函数 ==========

  /// 私有构造函数，仅供工厂方法使用。
  ///
  /// @param id 聚合根 ID（新创建时为 null）
  /// @param venueId 关联的 Venue ID
  /// @param year 评级年份
  /// @param ratingSystem 评价体系
  private VenueRatingAggregate(
      VenueRatingId id, Long venueId, int year, RatingSystem ratingSystem) {
    super(id);
    Assert.notNull(venueId, "Venue ID 不能为空");
    Assert.notNull(ratingSystem, "评价体系不能为空");
    if (year < MIN_YEAR || year > MAX_YEAR) {
      throw new IllegalArgumentException(
          String.format("年份必须在 %d-%d 范围内: %d", MIN_YEAR, MAX_YEAR, year));
    }
    this.venueId = venueId;
    this.year = year;
    this.ratingSystem = ratingSystem;
  }

  // ========== 工厂方法 ==========

  /// 创建新的评级记录。
  ///
  /// @param venueId 关联的 Venue ID
  /// @param year 评级年份
  /// @param ratingSystem 评价体系
  /// @param quartile 分区（可选）
  /// @param impactScore 影响力分数（可选）
  /// @return 新的评级聚合根
  public static VenueRatingAggregate create(
      Long venueId, int year, RatingSystem ratingSystem, String quartile, BigDecimal impactScore) {
    VenueRatingAggregate aggregate = new VenueRatingAggregate(null, venueId, year, ratingSystem);
    aggregate.quartile = quartile;
    aggregate.impactScore = impactScore;
    aggregate.fetchedAt = Instant.now();
    return aggregate;
  }

  /// 从持久化状态恢复聚合根（Repository 使用）。
  ///
  /// @param id 聚合根 ID
  /// @param venueId 关联的 Venue ID
  /// @param year 评级年份
  /// @param ratingSystem 评价体系
  /// @param version 乐观锁版本
  /// @return 恢复的评级聚合根
  public static VenueRatingAggregate restore(
      VenueRatingId id, Long venueId, int year, RatingSystem ratingSystem, Long version) {
    VenueRatingAggregate aggregate = new VenueRatingAggregate(id, venueId, year, ratingSystem);
    aggregate.assignVersion(version);
    return aggregate;
  }

  /// 恢复可变状态。
  ///
  /// **注意**：此方法仅供基础设施层 Converter 在从数据库恢复聚合根时使用，
  /// 业务代码应使用 `updateRatingDetails()`、`updateQuartileAndScore()` 等业务方法。
  ///
  /// @param quartile 分区
  /// @param impactScore 影响力分数
  /// @param ratingData 评级详情 JSON
  /// @param categories 学科分类 JSON
  /// @param sourceUrl 数据来源 URL
  /// @param fetchedAt 数据获取时间
  public void restoreState(
      String quartile,
      BigDecimal impactScore,
      String ratingData,
      String categories,
      String sourceUrl,
      Instant fetchedAt) {
    this.quartile = quartile;
    this.impactScore = impactScore;
    this.ratingData = ratingData;
    this.categories = categories;
    this.sourceUrl = sourceUrl;
    this.fetchedAt = fetchedAt;
  }

  // ========== 业务方法 ==========

  /// 更新评级详情。
  ///
  /// @param ratingData 评级详情 JSON
  /// @param categories 学科分类 JSON
  public void updateRatingDetails(String ratingData, String categories) {
    this.ratingData = ratingData;
    this.categories = categories;
    markDirty();
  }

  /// 更新分区和影响力分数。
  ///
  /// @param quartile 分区
  /// @param impactScore 影响力分数
  public void updateQuartileAndScore(String quartile, BigDecimal impactScore) {
    this.quartile = quartile;
    this.impactScore = impactScore;
    markDirty();
  }

  /// 记录数据来源。
  ///
  /// @param sourceUrl 数据来源 URL
  /// @param fetchedAt 数据获取时间（为 null 时使用当前时间）
  public void recordSource(String sourceUrl, Instant fetchedAt) {
    this.sourceUrl = sourceUrl;
    this.fetchedAt = fetchedAt != null ? fetchedAt : Instant.now();
    markDirty();
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
  /// @return 标准化后的分区，如果无法标准化则返回原值，空值返回 null
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
