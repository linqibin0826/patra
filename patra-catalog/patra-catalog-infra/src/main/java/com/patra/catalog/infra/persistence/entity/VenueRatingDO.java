package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// 载体评级数据库实体，映射到表 `cat_venue_rating`。
///
/// 表结构：存储载体的年度评级数据（JCR 影响因子 / 中科院分区 / Scopus CiteScore 等）。
/// 每个 Venue 每年每种评价体系一条记录（uk_venue_year_system 唯一约束）。
///
/// 关键字段说明：
///
/// - `rating_system` 评级体系：JCR/CAS/SCOPUS
/// - `quartile` 分区：Q1-Q4（JCR/Scopus）或 1-4区（中科院）
/// - `impact_score` 影响力分数：JIF/CiteScore/复合IF
/// - `rating_data` 评级详情 JSON（各体系特有字段）
/// - `categories` 学科分类及分区 JSON
///
/// rating_data JSON 结构示例：
///
/// **JCR**:
/// ```json
/// {"jif": 42.778, "jif_without_self_cites": 41.234, "jci": 5.12, "eigenfactor": 0.45678}
/// ```
///
/// **CAS（中科院分区）**:
/// ```json
/// {"partition": "1区", "is_top": true, "trend": "up", "comprehensive_if": 45.678}
/// ```
///
/// **SCOPUS**:
/// ```json
/// {"cite_score": 12.5, "snip": 2.345, "sjr": 5.678, "percentile": 98}
/// ```
///
/// 索引说明：
///
/// - 唯一索引 `uk_venue_year_system`: venue_id + year + rating_system 唯一
/// - 普通索引 `idx_rating_system`: 评级体系
/// - 普通索引 `idx_year`: 年份
/// - 普通索引 `idx_quartile`: 分区
/// - 普通索引 `idx_impact_score`: 影响力分数
///
/// @author linqibin
/// @since 0.1.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_venue_rating", autoResultMap = true)
public class VenueRatingDO extends BaseDO {

  // ========================================
  // 关联信息
  // ========================================

  /// 载体 ID（外键：cat_venue.id）
  @TableField("venue_id")
  private Long venueId;

  /// 评级年份
  @TableField("year")
  private Short year;

  /// 评级体系：JCR/CAS/SCOPUS
  @TableField("rating_system")
  private String ratingSystem;

  // ========================================
  // 通用评级字段（冗余，高频查询优化）
  // ========================================

  /// 分区：Q1-Q4（JCR/Scopus）或 1-4区（中科院）
  @TableField("quartile")
  private String quartile;

  /// 影响力分数（JIF/CiteScore/复合IF）
  @TableField("impact_score")
  private BigDecimal impactScore;

  // ========================================
  // 评级详情（各体系特有数据）
  // ========================================

  /// 评级详情 JSON（JCR: jif/jci/eigenfactor; CAS: is_top/trend; Scopus: snip/sjr）
  @TableField(value = "rating_data", typeHandler = JacksonTypeHandler.class)
  private JsonNode ratingData;

  /// 学科分类及分区（JSON 数组）
  @TableField(value = "categories", typeHandler = JacksonTypeHandler.class)
  private JsonNode categories;

  // ========================================
  // 数据来源
  // ========================================

  /// 数据来源 URL
  @TableField("source_url")
  private String sourceUrl;

  /// 数据获取时间
  @TableField("fetched_at")
  private Instant fetchedAt;
}
