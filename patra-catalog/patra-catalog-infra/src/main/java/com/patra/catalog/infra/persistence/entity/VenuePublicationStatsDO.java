package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// 载体年度发文统计数据库实体，映射到表 `cat_venue_publication_stats`。
///
/// 表结构：存储载体的年度发文量和引用量统计数据，支持时序分析。
///
/// 关键字段说明：
///
/// - `venue_id` 载体 ID，外键关联 cat_venue.id
/// - `year` 统计年份（1900-2100）
/// - `works_count` 该年发表作品数量
/// - `cited_by_count` 该年被引用次数
/// - `oa_works_count` 该年 OA 作品数量（可选）
///
/// 索引说明：
///
/// - 唯一索引 `uk_venue_year`: (venue_id, year) 保证每年只有一条记录
/// - 普通索引 `idx_year`: year 支持按年份统计
/// - 普通索引 `idx_works_count`: works_count 支持排序
/// - 普通索引 `idx_cited_by_count`: cited_by_count 支持排序
///
/// 数据来源：
///
/// 主要来自 OpenAlex Source 的 `counts_by_year` 数组，每年一条记录。
///
/// 使用场景：
///
/// 1. 分析期刊历年发文量和被引趋势
/// 2. 计算期刊的增长率和影响力变化
/// 3. 与其他期刊进行年度对比分析
///
/// @author linqibin
/// @since 0.1.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_venue_publication_stats")
public class VenuePublicationStatsDO extends BaseDO {

  /// 载体 ID（外键：cat_venue.id）
  @TableField("venue_id")
  private Long venueId;

  /// 统计年份（1900-2100）
  @TableField("year")
  private Short year;

  /// 该年发表作品数量
  @TableField("works_count")
  private Integer worksCount;

  /// 该年被引用次数
  @TableField("cited_by_count")
  private Integer citedByCount;

  /// 该年 OA 作品数量（可选）
  @TableField("oa_works_count")
  private Integer oaWorksCount;
}
