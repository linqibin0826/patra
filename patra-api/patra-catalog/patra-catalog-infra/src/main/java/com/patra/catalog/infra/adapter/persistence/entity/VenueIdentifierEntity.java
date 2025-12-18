package com.patra.catalog.infra.adapter.persistence.entity;

import com.patra.starter.jpa.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/// 载体标识符 JPA 实体，映射到表 `cat_venue_identifier`。
///
/// **表结构**：存储载体的各类标识符，支持一对多关系（如一个期刊有多个 ISSN）。
///
/// **关键字段说明**：
///
/// - `venue_id` 载体 ID，外键关联 cat_venue.id
/// - `identifier_type` 标识符类型：OPENALEX/ISSN/ISSN_L/ISBN/NLM/MAG/FATCAT/WIKIDATA
/// - `identifier_value` 标识符值
/// - `is_primary` 是否首选标识符（同类型中只能有一个 primary）
///
/// **索引说明**：
///
/// - 唯一索引 `uk_venue_type_value`: (venue_id, identifier_type, identifier_value) 防止重复
/// - 复合索引 `idx_type_value`: (identifier_type, identifier_value) 支持按标识符反查载体
/// - 普通索引 `idx_venue_id`: venue_id 支持查询载体的所有标识符
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(
    name = "cat_venue_identifier",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_venue_type_value",
          columnNames = {"venue_id", "identifier_type", "identifier_value"})
    },
    indexes = {
      @Index(name = "idx_venue_id", columnList = "venue_id"),
      @Index(name = "idx_type_value", columnList = "identifier_type, identifier_value")
    })
public class VenueIdentifierEntity extends BaseJpaEntity {

  /// 载体 ID（外键：cat_venue.id）
  @Column(name = "venue_id", nullable = false)
  private Long venueId;

  /// 标识符类型：OPENALEX/ISSN/ISSN_L/ISBN/NLM/MAG/FATCAT/WIKIDATA/CODEN
  @Column(name = "identifier_type", nullable = false, length = 20)
  private String identifierType;

  /// 标识符值
  @Column(name = "identifier_value", nullable = false, length = 100)
  private String identifierValue;

  /// 是否首选标识符（同类型中，0=否，1=是）
  @Column(name = "is_primary")
  private Boolean isPrimary;
}
