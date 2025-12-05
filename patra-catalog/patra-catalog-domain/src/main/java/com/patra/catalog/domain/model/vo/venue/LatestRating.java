package com.patra.catalog.domain.model.vo.venue;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/// 最新评级快照值对象。存储期刊的最新评级信息快照，用于高频查询优化。
///
/// 设计原则：
///
/// - 不可变性：Record 自动提供
/// - 冗余设计：作为 `cat_venue_rating` 表数据的快照，避免关联查询
/// - 多体系支持：支持 JCR、中科院分区、Scopus 等多种评价体系
///
/// 冗余字段同步策略：
///
/// 每当 `cat_venue_rating` 表导入新年度评级数据时，应同步更新 `cat_venue` 表
/// 的最新评级快照字段。更新逻辑：
///
/// 1. 查找该 venue_id 最新年份的评级记录
/// 2. 按评价体系优先级选择（JCR > CAS > SCOPUS）
/// 3. 更新主表的 `latest_impact_score`、`latest_quartile` 等字段
///
/// 使用示例：
///
/// ```java
/// // JCR 评级
/// LatestRating jcr = LatestRating.of(
///     new BigDecimal("42.778"), "Q1", "JCR", 2023);
///
/// // 中科院分区
/// LatestRating cas = LatestRating.of(
///     new BigDecimal("45.678"), "1区", "CAS", 2023);
///
/// // 无评级
/// LatestRating none = LatestRating.empty();
/// ```
///
/// @param impactScore 影响力分数（JIF/CiteScore/复合IF）
/// @param quartile 分区（Q1-Q4 或 1-4区）
/// @param ratingSystem 评价体系代码（JCR/CAS/SCOPUS）
/// @param year 评级年份
/// @author linqibin
/// @since 0.1.0
public record LatestRating(
    BigDecimal impactScore, String quartile, String ratingSystem, Integer year)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 空评级实例（单例模式）。
  private static final LatestRating EMPTY = new LatestRating(null, null, null, null);

  /// 创建最新评级快照。
  ///
  /// @param impactScore 影响力分数
  /// @param quartile 分区
  /// @param ratingSystem 评价体系
  /// @param year 评级年份
  /// @return 最新评级值对象
  public static LatestRating of(
      BigDecimal impactScore, String quartile, String ratingSystem, Integer year) {
    return new LatestRating(impactScore, quartile, ratingSystem, year);
  }

  /// 获取空评级实例。
  ///
  /// @return 空评级值对象
  public static LatestRating empty() {
    return EMPTY;
  }

  /// 判断是否有评级数据。
  ///
  /// @return true 如果有评级数据
  public boolean hasRating() {
    return ratingSystem != null && year != null;
  }

  /// 判断是否有影响力分数。
  ///
  /// @return true 如果有影响力分数
  public boolean hasImpactScore() {
    return impactScore != null;
  }

  /// 判断是否有分区信息。
  ///
  /// @return true 如果有分区信息
  public boolean hasQuartile() {
    return quartile != null && !quartile.isBlank();
  }

  /// 判断是否为 JCR 评级。
  ///
  /// @return true 如果是 JCR 评级
  public boolean isJcr() {
    return "JCR".equals(ratingSystem);
  }

  /// 判断是否为中科院分区。
  ///
  /// @return true 如果是中科院分区
  public boolean isCas() {
    return "CAS".equals(ratingSystem);
  }

  /// 判断是否为 Scopus 评级。
  ///
  /// @return true 如果是 Scopus 评级
  public boolean isScopus() {
    return "SCOPUS".equals(ratingSystem);
  }

  /// 判断是否为顶级分区（Q1 或 1区）。
  ///
  /// @return true 如果是顶级分区
  public boolean isTopQuartile() {
    if (quartile == null) {
      return false;
    }
    return "Q1".equalsIgnoreCase(quartile) || "1区".equals(quartile) || "1".equals(quartile);
  }

  /// 获取标准化的分区等级（1-4）。
  ///
  /// @return 分区等级数字，无法解析则返回 null
  public Integer getQuartileLevel() {
    if (quartile == null) {
      return null;
    }
    String normalized = quartile.toUpperCase().trim();
    if (normalized.matches("Q[1-4]")) {
      return Integer.parseInt(normalized.substring(1));
    }
    if (normalized.matches("[1-4]区?")) {
      return Integer.parseInt(normalized.substring(0, 1));
    }
    return null;
  }
}
