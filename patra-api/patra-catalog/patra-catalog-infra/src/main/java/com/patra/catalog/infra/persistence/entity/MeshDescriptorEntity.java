package com.patra.catalog.infra.persistence.entity;

import com.patra.starter.jpa.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/// MeSH 主题词 JPA 实体，映射到表 `cat_mesh_descriptor`。
///
/// **表结构**：存储 NLM MeSH 主题词核心信息，是医学文献标引的权威词表。
///
/// **数据规模**：约 3.5 万条主题词
///
/// **关键字段说明**：
///
/// - `ui` MeSH 唯一标识符（格式：D000001-D999999）
/// - `name` 主题词名称（首选术语，英文）
/// - `descriptor_class` 主题词类型（1-Topical/2-PublicationType/3-Geographicals/4-CheckTag）
/// - `active_status` 是否有效（false=已废弃，true=有效）
/// - `mesh_version` MeSH 版本年份（如 "2025"）
///
/// **索引说明**：
///
/// - 唯一索引 `uk_mesh_ui`: ui 支持高频精确查询
/// - 普通索引 `idx_name`: name 支持按名称查询
/// - 复合索引 `idx_active_version`: (active_status, mesh_version) 筛选某版本的有效主题词
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(
    name = "cat_mesh_descriptor",
    uniqueConstraints = {@UniqueConstraint(name = "uk_mesh_ui", columnNames = "ui")},
    indexes = {
      @Index(name = "idx_name", columnList = "name"),
      @Index(name = "idx_active_version", columnList = "active_status, mesh_version")
    })
public class MeshDescriptorEntity extends BaseJpaEntity {

  /// MeSH 唯一标识符（格式：D000001-D999999）
  @Column(name = "ui", nullable = false, length = 10)
  private String ui;

  /// 主题词名称（首选术语，英文）
  @Column(name = "name", nullable = false, length = 255)
  private String name;

  /// 主题词类型（1-Topical/2-PublicationType/3-Geographicals/4-CheckTag）
  @Column(name = "descriptor_class", length = 50)
  private String descriptorClass;

  /// 范围说明（定义和使用指南）
  @Column(name = "scope_note", columnDefinition = "TEXT")
  private String scopeNote;

  /// 注释（索引员使用的说明）
  @Column(name = "annotation", columnDefinition = "TEXT")
  private String annotation;

  /// 之前的索引方式（历史参考）
  @Column(name = "previous_indexing", columnDefinition = "TEXT")
  private String previousIndexing;

  /// 公共 MeSH 注释（面向用户）
  @Column(name = "public_mesh_note", columnDefinition = "TEXT")
  private String publicMeshNote;

  /// 另请参考（相关主题词建议）
  @Column(name = "consider_also", columnDefinition = "TEXT")
  private String considerAlso;

  /// 历史说明（记录主题词的历史使用规则）
  @Column(name = "history_note", columnDefinition = "TEXT")
  private String historyNote;

  /// 在线检索说明（检索策略指南）
  @Column(name = "online_note", columnDefinition = "TEXT")
  private String onlineNote;

  /// NLM 分类号
  @Column(name = "nlm_classification_number", length = 50)
  private String nlmClassificationNumber;

  /// 创建日期
  @Column(name = "date_created")
  private LocalDate dateCreated;

  /// 修订日期
  @Column(name = "date_revised")
  private LocalDate dateRevised;

  /// 确立日期
  @Column(name = "date_established")
  private LocalDate dateEstablished;

  /// 是否有效（false=已废弃，true=有效）
  @Column(name = "active_status", nullable = false)
  private Boolean activeStatus;

  /// MeSH 版本年份（如 "2025"）
  @Column(name = "mesh_version", length = 10)
  private String meshVersion;

  /// 其他元数据（JSON 扩展字段）
  @Column(name = "metadata", columnDefinition = "JSON")
  private String metadata;
}
