package dev.patra.catalog.infra.mesh.persistence.entity;

import dev.patra.tool.jpa.entity.ValueObjectJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/// SCR 药理作用 JPA 实体，映射到表 `cat_mesh_scr_pharmacological_action`。
///
/// **表结构**：存储 SCR 的药理作用（PharmacologicalActionList），指向 Descriptor。
///
/// **数据规模**：初始 15 万 / 年增长 1 万 / 5 年规模 20 万（约 50% 的化学物质 SCR 有药理作用）
///
/// **关键字段说明**：
///
/// - `scr_ui` SCR UI（关联 cat_mesh_scr.ui）
/// - `descriptor_ui` 药理作用主题词 UI（关联 cat_mesh_descriptor.ui）
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(name = "cat_mesh_scr_pharmacological_action")
public class MeshScrPharmacologicalActionEntity extends ValueObjectJpaEntity {

  /// SCR UI（关联：cat_mesh_scr.ui，格式：C000001）
  @Column(name = "scr_ui", nullable = false, length = 10)
  private String scrUi;

  /// 药理作用主题词 UI（关联：cat_mesh_descriptor.ui）
  @Column(name = "descriptor_ui", nullable = false, length = 10)
  private String descriptorUi;

  /// 主题词名称（冗余存储，避免查询时 JOIN）
  @Column(name = "descriptor_name", nullable = false, length = 255)
  private String descriptorName;
}
