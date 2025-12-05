package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// 载体数据源数据库实体，映射到表 `cat_venue_source_data`。
///
/// 表结构：存储各数据源的原始 JSON 和提取字段，支持数据溯源和审计。
/// 每个 Venue 可关联多个数据源记录（uk_venue_source 唯一约束）。
///
/// 关键字段说明：
///
/// - `source_code` 来源代码：OPENALEX/PUBMED/DOAJ/CROSSREF/JCR
/// - `source_id` 来源系统中的 ID（如 OpenAlex 的 S1234567890）
/// - `raw_data` 原始 JSON 数据（完整保存用于审计）
/// - `extracted_data` 提取的关键字段（JSON，便于查询）
/// - `fetched_at` 数据获取时间（本系统获取时间）
///
/// 索引说明：
///
/// - 唯一索引 `uk_venue_source`: venue_id + source_code 唯一
/// - 普通索引 `idx_source_code`: 来源代码
/// - 普通索引 `idx_fetched_at`: 获取时间
///
/// @author linqibin
/// @since 0.1.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_venue_source_data", autoResultMap = true)
public class VenueSourceDataDO extends BaseDO {

  // ========================================
  // 关联信息
  // ========================================

  /// 载体 ID（外键：cat_venue.id）
  @TableField("venue_id")
  private Long venueId;

  /// 来源代码：OPENALEX/PUBMED/DOAJ/CROSSREF/JCR
  @TableField("source_code")
  private String sourceCode;

  /// 来源系统中的 ID
  @TableField("source_id")
  private String sourceId;

  // ========================================
  // 数据内容
  // ========================================

  /// 原始 JSON 数据（完整保存）
  @TableField(value = "raw_data", typeHandler = JacksonTypeHandler.class)
  private JsonNode rawData;

  /// 提取的关键字段（JSON，便于查询）
  @TableField(value = "extracted_data", typeHandler = JacksonTypeHandler.class)
  private JsonNode extractedData;

  // ========================================
  // 来源时间
  // ========================================

  /// 来源系统创建时间
  @TableField("source_created_at")
  private LocalDate sourceCreatedAt;

  /// 来源系统更新时间
  @TableField("source_updated_at")
  private LocalDate sourceUpdatedAt;

  /// 数据获取时间
  @TableField("fetched_at")
  private Instant fetchedAt;
}
