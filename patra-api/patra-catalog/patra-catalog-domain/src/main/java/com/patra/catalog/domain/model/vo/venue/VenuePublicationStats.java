package com.patra.catalog.domain.model.vo.venue;

import cn.hutool.core.lang.Assert;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;

/// 载体年度发文统计值对象（不可变）。
///
/// **设计说明**：
///
/// - 作为值对象存在（不是实体）
/// - 使用 Record 实现不可变性
/// - 通过 `VenueRepository` 统一管理
/// - 每年一条记录，支持时序分析
/// - 专注于发文量和引用量统计（评级数据存储在 VenueRating 中）
///
/// **业务规则**：
///
/// - 同一载体每年只能有一条记录
/// - 年份范围：1900-2100
/// - worksCount 和 citedByCount 不能为负数
///
/// **数据来源**：
///
/// 主要来自 OpenAlex Source 的 `counts_by_year` 数组。
///
/// **示例**：
///
/// ```java
/// // 创建年度统计
/// VenuePublicationStats stats = VenuePublicationStats.create(2024, 1500, 25000);
///
/// // 创建含 OA 作品数的年度统计
/// VenuePublicationStats stats = VenuePublicationStats.create(2024, 1500, 25000, 800);
///
/// // 更新 OA 作品数（返回新实例）
/// VenuePublicationStats updated = stats.withOaWorksCount(900);
/// ```
///
/// @param year 统计年份（1900-2100）
/// @param worksCount 该年发表作品数量
/// @param citedByCount 该年被引用次数
/// @param oaWorksCount 该年 OA 作品数量（可选）
/// @author linqibin
/// @since 0.1.0
public record VenuePublicationStats(
    int year, int worksCount, int citedByCount, Integer oaWorksCount) implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 最小年份
  private static final int MIN_YEAR = 1900;

  /// 最大年份
  private static final int MAX_YEAR = 2100;

  /// 紧凑构造器：验证参数。
  public VenuePublicationStats {
    Assert.isTrue(
        year >= MIN_YEAR && year <= MAX_YEAR, "年份必须在 {}-{} 之间：{}", MIN_YEAR, MAX_YEAR, year);
    Assert.isTrue(worksCount >= 0, "发表作品数不能为负数：{}", worksCount);
    Assert.isTrue(citedByCount >= 0, "被引用次数不能为负数：{}", citedByCount);
    if (oaWorksCount != null) {
      Assert.isTrue(oaWorksCount >= 0, "OA 作品数不能为负数：{}", oaWorksCount);
      Assert.isTrue(oaWorksCount <= worksCount, "OA 作品数不能超过总作品数：{} > {}", oaWorksCount, worksCount);
    }
  }

  // ========== 工厂方法 ==========

  /// 创建年度统计（含 OA 作品数）。
  ///
  /// @param year 统计年份
  /// @param worksCount 发表作品数
  /// @param citedByCount 被引用次数
  /// @param oaWorksCount OA 作品数
  /// @return 年度统计值对象
  public static VenuePublicationStats create(
      int year, int worksCount, int citedByCount, Integer oaWorksCount) {
    return new VenuePublicationStats(year, worksCount, citedByCount, oaWorksCount);
  }

  /// 创建年度统计（不含 OA 作品数）。
  ///
  /// @param year 统计年份
  /// @param worksCount 发表作品数
  /// @param citedByCount 被引用次数
  /// @return 年度统计值对象
  public static VenuePublicationStats create(int year, int worksCount, int citedByCount) {
    return new VenuePublicationStats(year, worksCount, citedByCount, null);
  }

  /// 创建空年度统计。
  ///
  /// @param year 统计年份
  /// @return 年度统计值对象
  public static VenuePublicationStats empty(int year) {
    return new VenuePublicationStats(year, 0, 0, null);
  }

  // ========== with-style 方法（返回新实例） ==========

  /// 返回带新统计数据的新实例。
  ///
  /// @param worksCount 发表作品数
  /// @param citedByCount 被引用次数
  /// @return 新的年度统计值对象
  public VenuePublicationStats withCounts(int worksCount, int citedByCount) {
    return new VenuePublicationStats(this.year, worksCount, citedByCount, this.oaWorksCount);
  }

  /// 返回带新 OA 作品数的新实例。
  ///
  /// @param oaWorksCount OA 作品数
  /// @return 新的年度统计值对象
  public VenuePublicationStats withOaWorksCount(Integer oaWorksCount) {
    return new VenuePublicationStats(this.year, this.worksCount, this.citedByCount, oaWorksCount);
  }

  // ========== 查询方法 ==========

  /// 判断是否有 OA 作品数。
  ///
  /// @return true 如果有 OA 作品数
  public boolean hasOaWorksCount() {
    return oaWorksCount != null;
  }

  /// 计算平均每篇被引次数。
  ///
  /// @return 平均被引次数，如果无作品则返回 0
  public BigDecimal getAverageCitations() {
    if (worksCount == 0) {
      return BigDecimal.ZERO;
    }
    return BigDecimal.valueOf(citedByCount)
        .divide(BigDecimal.valueOf(worksCount), 2, RoundingMode.HALF_UP);
  }

  /// 计算 OA 比例。
  ///
  /// @return OA 比例（0-1），如果无作品或无 OA 数据则返回 null
  public BigDecimal getOaRatio() {
    if (worksCount == 0 || oaWorksCount == null) {
      return null;
    }
    return BigDecimal.valueOf(oaWorksCount)
        .divide(BigDecimal.valueOf(worksCount), 4, RoundingMode.HALF_UP);
  }

  @Override
  public String toString() {
    return String.format(
        "VenuePublicationStats[year=%d, works=%d, cited=%d, oa=%s]",
        year, worksCount, citedByCount, oaWorksCount);
  }
}
