package com.patra.catalog.domain.model.entity;

import cn.hutool.core.lang.Assert;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import lombok.Getter;

/// 载体年度发文统计实体（聚合内实体，不是聚合根）。
///
/// 设计说明：
///
/// - 作为 VenueAggregate 的聚合内实体存在
/// - 与 Venue 具有相同的生命周期
/// - 每年一条记录，支持时序分析
/// - 专注于发文量和引用量统计（评级数据存储在 VenueRating 中）
///
/// 业务规则：
///
/// - 同一载体每年只能有一条记录
/// - 年份范围：1900-2100
/// - worksCount 和 citedByCount 不能为负数
///
/// 数据来源：
///
/// 主要来自 OpenAlex Source 的 `counts_by_year` 数组。
///
/// 使用示例：
///
/// ```java
/// // 创建年度统计
/// VenuePublicationStats stats = VenuePublicationStats.create(2024, 1500, 25000);
///
/// // 创建含 OA 作品数的年度统计
/// VenuePublicationStats stats = VenuePublicationStats.create(2024, 1500, 25000, 800);
/// ```
///
/// @author linqibin
/// @since 0.1.0
@Getter
public class VenuePublicationStats implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 最小年份
  private static final int MIN_YEAR = 1900;

  /// 最大年份
  private static final int MAX_YEAR = 2100;

  // ========== 标识符 ==========

  /// 主键 ID（由 Repository 在持久化时分配）
  private Long id;

  // ========== 业务字段 ==========

  /// 统计年份（1900-2100）
  private final int year;

  /// 该年发表作品数量
  private int worksCount;

  /// 该年被引用次数
  private int citedByCount;

  /// 该年 OA 作品数量（可选）
  private Integer oaWorksCount;

  /// 私有构造函数。
  ///
  /// @param id 主键 ID（新建时为 null）
  /// @param year 统计年份
  /// @param worksCount 发表作品数
  /// @param citedByCount 被引用次数
  /// @param oaWorksCount OA 作品数（可选）
  private VenuePublicationStats(
      Long id, int year, int worksCount, int citedByCount, Integer oaWorksCount) {
    Assert.isTrue(
        year >= MIN_YEAR && year <= MAX_YEAR, "年份必须在 {}-{} 之间：{}", MIN_YEAR, MAX_YEAR, year);
    Assert.isTrue(worksCount >= 0, "发表作品数不能为负数：{}", worksCount);
    Assert.isTrue(citedByCount >= 0, "被引用次数不能为负数：{}", citedByCount);
    if (oaWorksCount != null) {
      Assert.isTrue(oaWorksCount >= 0, "OA 作品数不能为负数：{}", oaWorksCount);
      Assert.isTrue(oaWorksCount <= worksCount, "OA 作品数不能超过总作品数：{} > {}", oaWorksCount, worksCount);
    }

    this.id = id;
    this.year = year;
    this.worksCount = worksCount;
    this.citedByCount = citedByCount;
    this.oaWorksCount = oaWorksCount;
  }

  // ========== 工厂方法 ==========

  /// 创建年度统计（含 OA 作品数）。
  ///
  /// @param year 统计年份
  /// @param worksCount 发表作品数
  /// @param citedByCount 被引用次数
  /// @param oaWorksCount OA 作品数
  /// @return 年度统计实体
  public static VenuePublicationStats create(
      int year, int worksCount, int citedByCount, Integer oaWorksCount) {
    return new VenuePublicationStats(null, year, worksCount, citedByCount, oaWorksCount);
  }

  /// 创建年度统计（不含 OA 作品数）。
  ///
  /// @param year 统计年份
  /// @param worksCount 发表作品数
  /// @param citedByCount 被引用次数
  /// @return 年度统计实体
  public static VenuePublicationStats create(int year, int worksCount, int citedByCount) {
    return new VenuePublicationStats(null, year, worksCount, citedByCount, null);
  }

  /// 创建空年度统计。
  ///
  /// @param year 统计年份
  /// @return 年度统计实体
  public static VenuePublicationStats empty(int year) {
    return new VenuePublicationStats(null, year, 0, 0, null);
  }

  /// 从持久化状态重建实体（由 Repository 使用）。
  ///
  /// @param id 主键 ID
  /// @param year 统计年份
  /// @param worksCount 发表作品数
  /// @param citedByCount 被引用次数
  /// @param oaWorksCount OA 作品数
  /// @return 重建的实体
  public static VenuePublicationStats restore(
      Long id, int year, int worksCount, int citedByCount, Integer oaWorksCount) {
    return new VenuePublicationStats(id, year, worksCount, citedByCount, oaWorksCount);
  }

  // ========== 业务方法 ==========

  /// 设置 ID（由 Repository 在持久化后回写）。
  ///
  /// @param id 主键 ID
  public void assignId(Long id) {
    this.id = id;
  }

  /// 更新统计数据。
  ///
  /// @param worksCount 发表作品数
  /// @param citedByCount 被引用次数
  public void updateCounts(int worksCount, int citedByCount) {
    Assert.isTrue(worksCount >= 0, "发表作品数不能为负数：{}", worksCount);
    Assert.isTrue(citedByCount >= 0, "被引用次数不能为负数：{}", citedByCount);

    this.worksCount = worksCount;
    this.citedByCount = citedByCount;
  }

  /// 设置 OA 作品数。
  ///
  /// @param oaWorksCount OA 作品数
  /// @return 当前对象（支持链式调用）
  public VenuePublicationStats withOaWorksCount(Integer oaWorksCount) {
    if (oaWorksCount != null) {
      Assert.isTrue(oaWorksCount >= 0, "OA 作品数不能为负数：{}", oaWorksCount);
      Assert.isTrue(oaWorksCount <= worksCount, "OA 作品数不能超过总作品数：{} > {}", oaWorksCount, worksCount);
    }
    this.oaWorksCount = oaWorksCount;
    return this;
  }

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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof VenuePublicationStats that)) {
      return false;
    }
    // 业务相等性：年份
    return year == that.year;
  }

  @Override
  public int hashCode() {
    return Objects.hash(year);
  }
}
