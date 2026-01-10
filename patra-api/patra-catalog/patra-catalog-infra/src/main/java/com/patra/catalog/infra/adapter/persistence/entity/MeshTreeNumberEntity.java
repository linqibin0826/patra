package com.patra.catalog.infra.adapter.persistence.entity;

import com.patra.starter.jpa.entity.ValueObjectJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/// MeSH 树形编号 JPA 实体，映射到表 `cat_mesh_tree_number`。
///
/// **表结构**：存储 MeSH 主题词的树形编号，支持多位置和层次查询。
///
/// **数据规模**：约 8 万条（一个主题词平均 2.3 个位置）
///
/// **关键字段说明**：
///
/// - `descriptor_ui` 主题词 UI（关联 cat_mesh_descriptor.ui）
/// - `tree_number` 树形编号（如 C04.557.337.428）
/// - `tree_level` 层级深度（1-15）
/// - `is_primary` 是否主要位置
///
/// **索引说明**：
///
/// - 唯一索引 `uk_tree_number`: tree_number 保证编号唯一性
/// - 普通索引 `idx_descriptor_ui`: 支持查询某主题词的所有位置
/// - 前缀索引 `idx_tree_prefix`: 支持层次查询（LIKE "D12.%"）
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(
    name = "cat_mesh_tree_number",
    uniqueConstraints = {@UniqueConstraint(name = "uk_tree_number", columnNames = "tree_number")},
    indexes = {
      @Index(name = "idx_descriptor_ui", columnList = "descriptor_ui"),
      @Index(name = "idx_tree_level", columnList = "tree_level, descriptor_ui")
    })
public class MeshTreeNumberEntity extends ValueObjectJpaEntity {

  /// 主题词 UI（关联：cat_mesh_descriptor.ui）
  @Column(name = "descriptor_ui", nullable = false, length = 10)
  private String descriptorUi;

  /// 树形编号（如 C04.557.337.428，最多 15 层约 59 字符）
  @Column(name = "tree_number", nullable = false, length = 100)
  private String treeNumber;

  /// 层级深度（1-15，自动计算）
  @Column(name = "tree_level", nullable = false)
  private Integer treeLevel;

  /// 是否主要位置（false=次要，true=主要）
  @Column(name = "is_primary", nullable = false)
  private Boolean isPrimary;
}
