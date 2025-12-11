package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// 统计快照数据库实体（CQRS 补充数据），映射到表 `cat_venue_stats`。
///
/// **设计说明**：
///
/// 与 `cat_venue` 表为 1:1 关系，通过 `venue_id` 唯一索引保证。
/// 存储载体的当前统计快照数据，主要来源于 OpenAlex。
///
/// **字段说明**：
///
/// | 字段 | 说明 | 来源 |
/// |------|------|------|
/// | works_count | 托管作品总数 | OpenAlex |
/// | cited_by_count | 被引用次数总计 | OpenAlex |
/// | h_index | H-Index 指数 | OpenAlex |
/// | i10_index | i10-Index 指数 | OpenAlex |
/// | two_year_mean_citedness | 两年平均引用率 | OpenAlex |
///
/// **注意**：
///
/// 这是当前快照数据，历史时序统计请查询 `cat_venue_publication_stats` 表。
///
/// @author linqibin
/// @since 0.1.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_venue_stats", autoResultMap = true)
public class VenueStatsDO extends BaseDO {

  // ========================================
  // 关联信息
  // ========================================

  /// 载体 ID（外键：cat_venue.id）
  @TableField("venue_id")
  private Long venueId;

  // ========================================
  // 统计指标（当前快照）
  // ========================================

  /// 托管作品总数
  @TableField("works_count")
  private Integer worksCount;

  /// 被引用次数总计
  @TableField("cited_by_count")
  private Integer citedByCount;

  /// H-Index 指数
  @TableField("h_index")
  private Integer hIndex;

  /// i10-Index 指数
  @TableField("i10_index")
  private Integer i10Index;

  /// 两年平均引用率
  @TableField("two_year_mean_citedness")
  private BigDecimal twoYearMeanCitedness;
}
