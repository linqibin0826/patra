package com.patra.catalog.infra.adapter.persistence.entity;

import com.patra.catalog.domain.model.enums.MeshRecordType;
import com.patra.starter.jpa.entity.ValueObjectJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/// MeSH 概念 JPA 实体，映射到表 `cat_mesh_concept`。
///
/// **表结构**：存储 MeSH 主题词和 SCR 的概念，支持概念级别的关联和检索。
///
/// **数据规模**：Descriptor 约 18 万条 + SCR 约 160 万条
///
/// **关键字段说明**：
///
/// - `record_type` 记录类型（DESCRIPTOR=主题词，SCR=补充概念）
/// - `owner_ui` 所有者 UI（Descriptor: D开头，SCR: C开头）
/// - `concept_ui` 概念唯一标识符（格式：M000001-M999999）
/// - `concept_name` 概念名称
/// - `is_preferred` 是否首选概念（每个记录只有一个）
/// - `registry_numbers` 注册号列表（JSON 数组）
///
/// **索引说明**：
///
/// - 唯一索引 `uk_concept_ui`: concept_ui 支持精确查询
/// - 复合索引 `idx_record_type_owner`: 支持按类型和所有者查询概念
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(
    name = "cat_mesh_concept",
    uniqueConstraints = {@UniqueConstraint(name = "uk_concept_ui", columnNames = "concept_ui")},
    indexes = {@Index(name = "idx_record_type_owner", columnList = "record_type, owner_ui")})
public class MeshConceptEntity extends ValueObjectJpaEntity {

  /// 记录类型（DESCRIPTOR=主题词，SCR=补充概念）
  @Enumerated(EnumType.STRING)
  @Column(name = "record_type", nullable = false, length = 20)
  private MeshRecordType recordType;

  /// 所有者 UI（Descriptor: D开头，SCR: C开头）
  @Column(name = "owner_ui", nullable = false, length = 10)
  private String ownerUi;

  /// 概念唯一标识符（格式：M000001-M999999）
  @Column(name = "concept_ui", nullable = false, length = 10)
  private String conceptUi;

  /// 概念名称
  @Column(name = "concept_name", nullable = false, length = 255)
  private String conceptName;

  /// 是否首选概念（每个主题词只有一个）
  @Column(name = "is_preferred", nullable = false)
  private Boolean isPreferred;

  /// CAS 类型 1 名称（化学物质专用，IUPAC 命名可能很长）
  @Column(name = "casn1_name", columnDefinition = "TEXT")
  private String casn1Name;

  /// 注册号列表（JSON 数组，2025 DTD 支持多个）
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "registry_numbers", columnDefinition = "JSON")
  private List<String> registryNumbers;

  /// 相关注册号列表（JSON 数组）
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "related_registry_numbers", columnDefinition = "JSON")
  private List<String> relatedRegistryNumbers;

  /// 范围说明
  @Column(name = "scope_note", columnDefinition = "TEXT")
  private String scopeNote;

  /// 翻译者英文范围说明
  @Column(name = "translators_english_scope_note", columnDefinition = "TEXT")
  private String translatorsEnglishScopeNote;

  /// 翻译者范围说明
  @Column(name = "translators_scope_note", columnDefinition = "TEXT")
  private String translatorsScopeNote;

  /// 概念状态（枚举值）
  @Column(name = "concept_status", length = 10)
  private String conceptStatus;
}
