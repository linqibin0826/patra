package com.patra.catalog.infra.adapter.persistence.entity;

import com.patra.starter.jpa.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/// SCR 映射关系 JPA 实体，映射到表 `cat_mesh_scr_heading_mapped_to`。
///
/// **表结构**：存储 SCR 到 Descriptor 的映射关系，这是 SCR 最核心的关系。
///
/// **数据规模**：初始 50 万 / 年增长 3 万 / 5 年规模 65 万（每个 SCR 平均 1.5 个映射）
///
/// **关键字段说明**：
///
/// - `scr_ui` SCR UI（关联 cat_mesh_scr.ui）
/// - `descriptor_ui` 映射到的主题词 UI（关联 cat_mesh_descriptor.ui）
/// - `qualifier_ui` 限定词 UI（可选，关联 cat_mesh_qualifier.ui）
///
/// **索引说明**：
///
/// - 复合唯一索引 `uk_scr_desc_qual`: 防止重复映射
/// - 普通索引 `idx_scr_ui`: 支持查询某 SCR 的所有映射
/// - 普通索引 `idx_descriptor_ui`: 支持反向查询
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(
    name = "cat_mesh_scr_heading_mapped_to",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_scr_desc_qual",
          columnNames = {"scr_ui", "descriptor_ui", "qualifier_ui"})
    },
    indexes = {
      @Index(name = "idx_scr_ui", columnList = "scr_ui"),
      @Index(name = "idx_descriptor_ui", columnList = "descriptor_ui")
    })
public class MeshScrHeadingMappedToEntity extends BaseJpaEntity {

  /// SCR UI（关联：cat_mesh_scr.ui，格式：C000001）
  @Column(name = "scr_ui", nullable = false, length = 10)
  private String scrUi;

  /// 映射到的主题词 UI（关联：cat_mesh_descriptor.ui，格式：D000001）
  @Column(name = "descriptor_ui", nullable = false, length = 10)
  private String descriptorUi;

  /// 限定词 UI（关联：cat_mesh_qualifier.ui，格式：Q000001，可选）
  @Column(name = "qualifier_ui", length = 10)
  private String qualifierUi;
}
