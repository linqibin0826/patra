package dev.linqibin.patra.catalog.infra.persistence.entity;

import com.patra.starter.jpa.entity.ValueObjectJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/// MeSH 组合条目 JPA 实体，映射到表 `cat_mesh_entry_combination`。
///
/// **表结构**：存储 MeSH 主题词的组合条目信息（EntryCombinationList）。
///
/// **数据规模**：约 500 条
///
/// **关键字段说明**：
///
/// - `descriptor_ui` 主题词 UI（关联 cat_mesh_descriptor.ui）
/// - `ecin_descriptor_ui` ECIN Descriptor UI（输入组合的主题词）
/// - `ecin_qualifier_ui` ECIN Qualifier UI（输入组合的限定词）
/// - `ecout_descriptor_ui` ECOUT Descriptor UI（输出组合的主题词）
/// - `ecout_qualifier_ui` ECOUT Qualifier UI（输出组合的限定词，可选）
///
/// **索引说明**：
///
/// - 普通索引 `idx_descriptor_ui`: 支持查询某主题词的所有组合条目
/// - 普通索引 `idx_ecin_descriptor`: ECIN 主题词索引
/// - 普通索引 `idx_ecout_descriptor`: ECOUT 主题词索引
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(
    name = "cat_mesh_entry_combination",
    indexes = {
      @Index(name = "idx_descriptor_ui", columnList = "descriptor_ui"),
      @Index(name = "idx_ecin_descriptor", columnList = "ecin_descriptor_ui"),
      @Index(name = "idx_ecout_descriptor", columnList = "ecout_descriptor_ui")
    })
public class MeshEntryCombinationEntity extends ValueObjectJpaEntity {

  /// 主题词 UI（关联：cat_mesh_descriptor.ui）
  @Column(name = "descriptor_ui", nullable = false, length = 10)
  private String descriptorUi;

  /// ECIN Descriptor UI（输入组合的主题词）
  @Column(name = "ecin_descriptor_ui", nullable = false, length = 10)
  private String ecinDescriptorUi;

  /// ECIN Qualifier UI（输入组合的限定词）
  @Column(name = "ecin_qualifier_ui", nullable = false, length = 10)
  private String ecinQualifierUi;

  /// ECOUT Descriptor UI（输出组合的主题词）
  @Column(name = "ecout_descriptor_ui", nullable = false, length = 10)
  private String ecoutDescriptorUi;

  /// ECOUT Qualifier UI（输出组合的限定词，可选）
  @Column(name = "ecout_qualifier_ui", length = 10)
  private String ecoutQualifierUi;
}
