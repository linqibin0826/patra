package com.patra.catalog.infra.adapter.persistence.entity;

import com.patra.starter.jpa.entity.ValueObjectJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/// 文献日期 JPA 实体，映射到表 `cat_publication_date`。
///
/// **表结构**：精确记录文献生命周期的各类日期，支持不完整日期表达。
///
/// **关键字段说明**：
///
/// - `publication_id` 文献 ID，外键关联 cat_publication.id
/// - `date_type` 日期类型：Received/Accepted/Published/Revised/EPub/PPub 等
/// - `year/month/day` 分别存储年月日（支持不完整日期）
/// - `date_precision` 精度：year/month/day
///
/// **索引说明**：
///
/// - 普通索引 `idx_publication`: publication_id 支持查询某文献的所有日期
/// - 普通索引 `idx_date_type`: date_type 支持按类型查询
/// - 普通索引 `idx_year`: year 支持按年份范围查询
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(
    name = "cat_publication_date",
    indexes = {
      @Index(name = "idx_publication", columnList = "publication_id"),
      @Index(name = "idx_date_type", columnList = "date_type"),
      @Index(name = "idx_year", columnList = "year")
    })
public class PublicationDateEntity extends ValueObjectJpaEntity {

  /// 文献 ID（外键：cat_publication.id）
  @Column(name = "publication_id", nullable = false)
  private Long publicationId;

  /// 日期类型：Received/Accepted/Published/Revised/EPub/PPub 等
  @Column(name = "date_type", nullable = false, length = 50)
  private String dateType;

  /// 日期值（仅完整日期时填充）
  @Column(name = "date_value")
  private LocalDate dateValue;

  /// 年份（必填）
  @Column(name = "year", nullable = false)
  private Integer year;

  /// 月份（1-12，可为空）
  @Column(name = "month")
  private Integer month;

  /// 日期（1-31，可为空）
  @Column(name = "day")
  private Integer day;

  /// 精度：year/month/day
  @Column(name = "date_precision", nullable = false, length = 10)
  private String datePrecision;

  /// 季节（如 "Spring 2024"）
  @Column(name = "season", length = 100)
  private String season;

  /// 原始日期字符串
  @Column(name = "date_string", length = 200)
  private String dateString;

  /// 是否主要日期
  @Column(name = "is_primary", nullable = false)
  private Boolean isPrimary = false;

  /// 顺序号
  @Column(name = "order_num")
  private Integer orderNum;
}
