package com.patra.catalog.infra.persistence.entity;

import com.patra.starter.jpa.entity.ValueObjectJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/// 机构名称 JPA 实体，映射到表 `cat_organization_name`。
///
/// 存储机构的多语言名称，包括 ROR 显示名、标签、别名、缩写等。
///
/// **名称类型说明**：
///
/// | 类型 | 说明 |
/// |------|------|
/// | ROR_DISPLAY | ROR 官方显示名（ror_display） |
/// | LABEL | 语言标签名（label） |
/// | ALIAS | 别名/历史名称（alias） |
/// | ACRONYM | 缩写/简称（acronym） |
///
/// **唯一性约束**：同一机构下，相同名称值和语言代码的组合必须唯一。
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
    name = "cat_organization_name",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_org_name",
          columnNames = {"org_id", "value", "lang"})
    },
    indexes = {
      @Index(name = "idx_org_name_org_id", columnList = "org_id"),
      @Index(name = "idx_org_name_value", columnList = "value")
    })
public class OrganizationNameEntity extends ValueObjectJpaEntity {

  /// 所属机构 ID（逻辑外键）。
  @Column(name = "org_id", nullable = false)
  private Long orgId;

  /// 名称文本。
  @Column(name = "value", nullable = false, length = 500)
  private String value;

  /// 名称类型集合（JSON 数组）。
  ///
  /// 一个名称可能有多个类型，如同时是 LABEL 和 ALIAS。
  /// 存储枚举代码：["LABEL", "ALIAS"]
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "types", columnDefinition = "JSON")
  private Set<String> types;

  /// 语言代码（ISO 639-1/639-3）。
  ///
  /// 可能为 null（如 ROR_DISPLAY 名称无语言标记）。
  @Column(name = "lang", length = 10)
  private String lang;
}
