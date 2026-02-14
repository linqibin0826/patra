package com.patra.catalog.infra.persistence.entity;

import com.patra.starter.jpa.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/// MeSH 限定词 JPA 实体，映射到表 `cat_mesh_qualifier`。
///
/// **表结构说明**：
///
/// 存储 MeSH 限定词，用于修饰主题词（如 "immunology" 限定 "Antibodies"）。
/// 限定词是主数据，约 80 条记录，数量稳定。
///
/// **关键字段**：
///
/// - `ui` - 限定词唯一标识符（格式：Q000001-Q999999），唯一约束
/// - `name` - 限定词名称（英文）
/// - `abbreviation` - 限定词缩写（如 DI, GE, IM）
/// - `tree_numbers` - 树形编号列表（JSON 数组）
///
/// **Hibernate 7.1 特性**：
///
/// - 使用 `@JdbcTypeCode(SqlTypes.JSON)` 直接映射 `List<String>` 到 MySQL JSON 类型
/// - 继承 `BaseJpaEntity` 获得软删除、审计、乐观锁功能
///
/// @author linqibin
/// @since 0.1.0
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
    name = "cat_mesh_qualifier",
    indexes = {
      @Index(name = "uk_qualifier_ui", columnList = "ui", unique = true),
      @Index(name = "idx_name", columnList = "name")
    })
public class MeshQualifierEntity extends BaseJpaEntity {

  /// 限定词唯一标识符（格式：Q000001-Q999999）。
  @Column(name = "ui", nullable = false, length = 10)
  private String ui;

  /// 限定词名称（英文）。
  @Column(name = "name", nullable = false, length = 100)
  private String name;

  /// 限定词缩写（如 DI, GE, IM）。
  @Column(name = "abbreviation", length = 10)
  private String abbreviation;

  /// 注释说明。
  @Column(name = "annotation", columnDefinition = "TEXT")
  private String annotation;

  /// 创建日期。
  @Column(name = "date_created")
  private LocalDate dateCreated;

  /// 修订日期。
  @Column(name = "date_revised")
  private LocalDate dateRevised;

  /// 确立日期。
  @Column(name = "date_established")
  private LocalDate dateEstablished;

  /// 是否有效（false=已废弃，true=有效）。
  @Column(name = "active_status")
  private Boolean activeStatus;

  /// MeSH 版本年份（如 "2025"）。
  @Column(name = "mesh_version", length = 10)
  private String meshVersion;

  /// 历史说明（记录限定词的历史使用规则）。
  @Column(name = "history_note", columnDefinition = "TEXT")
  private String historyNote;

  /// 在线检索说明（检索策略指南）。
  @Column(name = "online_note", columnDefinition = "TEXT")
  private String onlineNote;

  /// 树形编号列表（限定词在 MeSH 层级树中的位置）。
  ///
  /// 使用 Hibernate 7.1 的 `@JdbcTypeCode(SqlTypes.JSON)` 直接映射到 MySQL JSON 类型。
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "tree_numbers", columnDefinition = "JSON")
  private List<String> treeNumbers = new ArrayList<>();
}
