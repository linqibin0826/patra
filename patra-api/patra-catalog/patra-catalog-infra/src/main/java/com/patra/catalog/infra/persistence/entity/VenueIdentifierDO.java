package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// 载体标识符数据库实体，映射到表 `cat_venue_identifier`。
///
/// 表结构：存储载体的各类标识符，支持一对多关系（如一个期刊有多个 ISSN）。
///
/// 关键字段说明：
///
/// - `venue_id` 载体 ID，外键关联 cat_venue.id
/// - `identifier_type` 标识符类型：OPENALEX/ISSN/ISSN_L/ISBN/NLM/MAG/FATCAT/WIKIDATA
/// - `identifier_value` 标识符值
/// - `is_primary` 是否首选标识符（同类型中只能有一个 primary）
///
/// 索引说明：
///
/// - 唯一索引 `uk_venue_type_value`: (venue_id, identifier_type, identifier_value) 防止重复
/// - 复合索引 `idx_type_value`: (identifier_type, identifier_value) 支持按标识符反查载体
/// - 普通索引 `idx_venue_id`: venue_id 支持查询载体的所有标识符
///
/// 使用场景：
///
/// 1. 从 OpenAlex 导入时拆分 issn[] 数组，每个 ISSN 一条记录
/// 2. 支持按任意标识符（DOI、ISSN、NLM ID 等）反查载体
/// 3. 管理载体的多源标识符（同一期刊可能同时有 OpenAlex ID、NLM ID、Wikidata ID）
///
/// @author linqibin
/// @since 0.1.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_venue_identifier")
public class VenueIdentifierDO extends BaseDO {

  /// 载体 ID（外键：cat_venue.id）
  @TableField("venue_id")
  private Long venueId;

  /// 标识符类型：OPENALEX/ISSN/ISSN_L/ISBN/NLM/MAG/FATCAT/WIKIDATA
  @TableField("identifier_type")
  private String identifierType;

  /// 标识符值
  @TableField("identifier_value")
  private String identifierValue;

  /// 是否首选标识符（同类型中，0=否，1=是）
  @TableField("is_primary")
  private Boolean isPrimary;
}
