package com.patra.catalog.infra.adapter.persistence.entity;

import com.patra.starter.jpa.entity.ValueObjectJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/// 机构外部标识符 JPA 实体，映射到表 `cat_organization_external_id`。
///
/// 存储机构在其他系统中的标识符，如 GRID、ISNI、Wikidata 等。
///
/// **外部标识符类型**：
///
/// | 类型 | 说明 |
/// |------|------|
/// | GRID | 旧版 Global Research Identifier Database |
/// | ISNI | International Standard Name Identifier |
/// | WIKIDATA | Wikidata Q-identifier |
/// | FUNDREF | Crossref Funder Registry |
/// | RINGGOLD | Ringgold Identify Database |
///
/// **唯一性约束**：每个机构每种类型只能有一个外部标识符。
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "cat_organization_external_id",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_org_ext_id_type",
          columnNames = {"org_id", "id_type"})
    },
    indexes = {
      @Index(name = "idx_org_ext_id_org_id", columnList = "org_id"),
      @Index(name = "idx_org_ext_id_preferred", columnList = "preferred_value")
    })
public class OrganizationExternalIdEntity extends ValueObjectJpaEntity {

  /// 所属机构 ID（逻辑外键）。
  @Column(name = "org_id", nullable = false)
  private Long orgId;

  /// 标识符类型代码：GRID/ISNI/WIKIDATA/FUNDREF/RINGGOLD。
  @Column(name = "id_type", nullable = false, length = 20)
  private String idType;

  /// 所有标识符值（JSON 数组）。
  ///
  /// 某些类型可能有多个值，如多个 ISNI。
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "all_values", columnDefinition = "JSON", nullable = false)
  private List<String> allValues;

  /// 首选标识符值。
  ///
  /// 从 allValues 中选择的首选值，用于快速查询和显示。
  @Column(name = "preferred_value", nullable = false, length = 100)
  private String preferredValue;
}
