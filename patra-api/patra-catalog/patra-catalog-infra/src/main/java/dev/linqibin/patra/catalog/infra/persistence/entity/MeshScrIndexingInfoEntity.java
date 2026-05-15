package dev.linqibin.patra.catalog.infra.persistence.entity;

import com.patra.starter.jpa.entity.ValueObjectJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/// SCR 索引信息 JPA 实体，映射到表 `cat_mesh_scr_indexing_info`。
///
/// **表结构**：存储 SCR 的索引信息（IndexingInformationList），包含引用的主题词、限定词、其他 SCR。
///
/// **数据规模**：初始 20 万 / 年增长 1 万 / 5 年规模 25 万（约 60% 的 SCR 有索引信息）
///
/// **关键字段说明**：
///
/// - `scr_ui` SCR UI（关联 cat_mesh_scr.ui）
/// - `descriptor_ui` 引用的主题词 UI（可选）
/// - `qualifier_ui` 引用的限定词 UI（可选）
/// - `chemical_ui` 引用的化学物质 UI（可选，指向其他 SCR 的 UI）
///
/// **索引说明**：
///
/// - 普通索引 `idx_scr_ui`: 支持查询某 SCR 的索引信息
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(
    name = "cat_mesh_scr_indexing_info",
    indexes = {@Index(name = "idx_scr_ui", columnList = "scr_ui")})
public class MeshScrIndexingInfoEntity extends ValueObjectJpaEntity {

  /// SCR UI（关联：cat_mesh_scr.ui，格式：C000001）
  @Column(name = "scr_ui", nullable = false, length = 10)
  private String scrUi;

  /// 引用的主题词 UI（可选，关联：cat_mesh_descriptor.ui）
  @Column(name = "descriptor_ui", length = 10)
  private String descriptorUi;

  /// 引用的限定词 UI（可选，关联：cat_mesh_qualifier.ui）
  @Column(name = "qualifier_ui", length = 10)
  private String qualifierUi;

  /// 引用的化学物质 UI（可选，指向其他 SCR 的 UI）
  @Column(name = "chemical_ui", length = 10)
  private String chemicalUi;
}
