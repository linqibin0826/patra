package com.patra.catalog.infra.adapter.persistence.entity;

import com.patra.starter.jpa.entity.ChildJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/// 载体年度发文统计 JPA 实体，映射到表 `cat_venue_publication_stats`。
///
/// **表结构**：存储载体的年度发文量和引用量统计数据，支持时序分析。
///
/// **关键字段说明**：
///
/// - `venue_id` 载体 ID，外键关联 cat_venue.id
/// - `year` 统计年份（1900-2100）
/// - `works_count` 该年发表作品数量
/// - `cited_by_count` 该年被引用次数
/// - `oa_works_count` 该年 OA 作品数量（可选）
///
/// **索引说明**：
///
/// - 唯一索引 `uk_venue_year`: (venue_id, year) 保证每年只有一条记录
/// - 普通索引 `idx_year`: year 支持按年份统计
/// - 普通索引 `idx_works_count`: works_count 支持排序
/// - 普通索引 `idx_cited_by_count`: cited_by_count 支持排序
///
/// **数据来源**：
///
/// 主要来自 OpenAlex Source 的 `counts_by_year` 数组，每年一条记录。
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(
    name = "cat_venue_publication_stats",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_venue_year",
          columnNames = {"venue_id", "year"})
    },
    indexes = {
      @Index(name = "idx_venue_id", columnList = "venue_id"),
      @Index(name = "idx_year", columnList = "year"),
      @Index(name = "idx_works_count", columnList = "works_count"),
      @Index(name = "idx_cited_by_count", columnList = "cited_by_count")
    })
public class VenuePublicationStatsEntity extends ChildJpaEntity {

  /// 载体 ID（外键：cat_venue.id）
  @Column(name = "venue_id", nullable = false)
  private Long venueId;

  /// 统计年份（1900-2100）
  @Column(name = "year", nullable = false)
  private Short year;

  /// 该年发表作品数量
  @Column(name = "works_count")
  private Integer worksCount;

  /// 该年被引用次数
  @Column(name = "cited_by_count")
  private Integer citedByCount;

  /// 该年 OA 作品数量（可选）
  @Column(name = "oa_works_count")
  private Integer oaWorksCount;
}
