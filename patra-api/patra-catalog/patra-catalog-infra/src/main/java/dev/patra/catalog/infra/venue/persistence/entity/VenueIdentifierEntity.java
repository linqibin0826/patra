package dev.patra.catalog.infra.venue.persistence.entity;

import dev.patra.tool.jpa.entity.ValueObjectJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
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
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(name = "cat_venue_identifier")
public class VenueIdentifierEntity extends ValueObjectJpaEntity {

  /// 载体 ID（引用 cat_venue.id，不建外键）
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
