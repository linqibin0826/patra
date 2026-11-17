package dev.patra.catalog.infra.mesh.persistence.entity;

import dev.patra.tool.jpa.entity.ValueObjectJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
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
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(name = "cat_mesh_scr_heading_mapped_to")
public class MeshScrHeadingMappedToEntity extends ValueObjectJpaEntity {

  /// SCR UI（关联：cat_mesh_scr.ui，格式：C000001）
  @Column(name = "scr_ui", nullable = false, length = 10)
  private String scrUi;

  /// 映射到的主题词 UI（关联：cat_mesh_descriptor.ui，格式：D000001）
  @Column(name = "descriptor_ui", nullable = false, length = 10)
  private String descriptorUi;

  /// 限定词 UI（关联：cat_mesh_qualifier.ui，格式：Q000001，可选）
  @Column(name = "qualifier_ui", length = 10)
  private String qualifierUi;

  /// 是否为主要主题词（Major Topic）
  @Column(name = "major_topic", nullable = false)
  private boolean majorTopic = false;
}
