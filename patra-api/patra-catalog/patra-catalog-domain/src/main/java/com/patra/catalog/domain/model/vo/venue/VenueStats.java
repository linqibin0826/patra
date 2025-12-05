package com.patra.catalog.domain.model.vo.venue;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/// 载体统计快照值对象。封装载体的当前累计统计指标。
///
/// 设计原则：
///
/// - 不可变性：Record 自动提供
/// - 时点快照：表示某一时刻的累计统计数据
/// - 与年度指标区分：VenueStats 是当前快照，VenuePublicationStats 是年度时序数据
///
/// 数据来源：
///
/// 主要来自 OpenAlex Source 的 `works_count`、`cited_by_count`、
/// `summary_stats.h_index`、`summary_stats.i10_index`、
/// `summary_stats.2yr_mean_citedness` 字段。
///
/// 使用示例：
///
/// ```java
/// // 创建完整统计快照
/// VenueStats stats = VenueStats.of(150000, 2500000, 285, 1200, new BigDecimal("3.45"));
///
/// // 创建基础统计快照（无高级指标）
/// VenueStats basic = VenueStats.ofBasic(150000, 2500000);
/// ```
///
/// @param worksCount 发表作品总数
/// @param citedByCount 被引用总次数
/// @param hIndex H 指数（可选）
/// @param i10Index i10 指数（引用次数 ≥10 的论文数，可选）
/// @param twoYearMeanCitedness 两年平均被引次数（可选）
/// @author linqibin
/// @since 0.1.0
public record VenueStats(
    Integer worksCount,
    Integer citedByCount,
    Integer hIndex,
    Integer i10Index,
    BigDecimal twoYearMeanCitedness)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 创建完整统计快照。
  ///
  /// @param worksCount 发表作品总数
  /// @param citedByCount 被引用总次数
  /// @param hIndex H 指数
  /// @param i10Index i10 指数
  /// @param twoYearMeanCitedness 两年平均被引次数
  /// @return 统计快照值对象
  public static VenueStats of(
      Integer worksCount,
      Integer citedByCount,
      Integer hIndex,
      Integer i10Index,
      BigDecimal twoYearMeanCitedness) {
    return new VenueStats(worksCount, citedByCount, hIndex, i10Index, twoYearMeanCitedness);
  }

  /// 创建基础统计快照（无高级指标）。
  ///
  /// @param worksCount 发表作品总数
  /// @param citedByCount 被引用总次数
  /// @return 统计快照值对象
  public static VenueStats ofBasic(Integer worksCount, Integer citedByCount) {
    return new VenueStats(worksCount, citedByCount, null, null, null);
  }

  /// 创建空统计快照。
  ///
  /// @return 空统计快照值对象
  public static VenueStats empty() {
    return new VenueStats(0, 0, null, null, null);
  }

  /// 判断是否有 H 指数。
  ///
  /// @return true 如果有 H 指数
  public boolean hasHIndex() {
    return hIndex != null;
  }

  /// 判断是否有 i10 指数。
  ///
  /// @return true 如果有 i10 指数
  public boolean hasI10Index() {
    return i10Index != null;
  }

  /// 判断是否有两年平均被引次数。
  ///
  /// @return true 如果有两年平均被引次数
  public boolean hasTwoYearMeanCitedness() {
    return twoYearMeanCitedness != null;
  }

  /// 计算平均每篇被引次数。
  ///
  /// @return 平均被引次数，如果无作品则返回 0
  public BigDecimal getAverageCitations() {
    if (worksCount == null || worksCount == 0 || citedByCount == null) {
      return BigDecimal.ZERO;
    }
    return BigDecimal.valueOf(citedByCount)
        .divide(BigDecimal.valueOf(worksCount), 2, java.math.RoundingMode.HALF_UP);
  }
}
