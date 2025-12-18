package com.patra.catalog.infra.adapter.persistence.entity;

import com.patra.starter.jpa.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
/// **表结构**：存储 MeSH 主题词下的概念，支持概念级别的关联和检索。
///
/// **数据规模**：约 18 万条（一个主题词平均 5-6 个概念）
///
/// **关键字段说明**：
///
/// - `descriptor_ui` 主题词 UI（关联 cat_mesh_descriptor.ui）
/// - `concept_ui` 概念唯一标识符（格式：M000001-M999999）
/// - `concept_name` 概念名称
/// - `is_preferred` 是否首选概念（每个主题词只有一个）
/// - `registry_numbers` 注册号列表（JSON 数组）
///
/// **索引说明**：
///
/// - 唯一索引 `uk_concept_ui`: concept_ui 支持精确查询
/// - 普通索引 `idx_descriptor_ui`: 支持查询某主题词的所有概念
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(
    name = "cat_mesh_concept",
    uniqueConstraints = {@UniqueConstraint(name = "uk_concept_ui", columnNames = "concept_ui")},
    indexes = {@Index(name = "idx_descriptor_ui", columnList = "descriptor_ui")})
public class MeshConceptEntity extends BaseJpaEntity {

  /// 主题词 UI（关联：cat_mesh_descriptor.ui）
  @Column(name = "descriptor_ui", nullable = false, length = 10)
  private String descriptorUi;

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
