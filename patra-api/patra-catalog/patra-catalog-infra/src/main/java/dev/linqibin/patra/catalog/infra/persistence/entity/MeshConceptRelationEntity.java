package dev.linqibin.patra.catalog.infra.persistence.entity;

import com.patra.starter.jpa.entity.ValueObjectJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/// MeSH 概念关系 JPA 实体，映射到表 `cat_mesh_concept_relation`。
///
/// **表结构**：存储概念关系（ConceptRelationList），记录同一主题词内不同概念之间的语义关系。
///
/// **数据规模**：约 5 万条
///
/// **关键字段说明**：
///
/// - `descriptor_ui` 主题词 UI（关联 cat_mesh_descriptor.ui）
/// - `concept_ui` 所属概念 UI（拥有此关系列表的概念）
/// - `is_preferred` 所属概念是否为首选概念
/// - `relation_name` 关系类型（NRW=Narrower/BRD=Broader/REL=Related）
/// - `concept1_ui` 概念 1 UI（DTD 定义总是首选概念）
/// - `concept2_ui` 概念 2 UI（关联概念）
///
/// **索引说明**：
///
/// - 普通索引 `idx_descriptor_ui`: 支持查询某主题词的所有概念关系
/// - 普通索引 `idx_concept`: 支持查询某概念的所有关系
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(
    name = "cat_mesh_concept_relation",
    indexes = {
      @Index(name = "idx_descriptor_ui", columnList = "descriptor_ui"),
      @Index(name = "idx_concept", columnList = "concept_ui")
    })
public class MeshConceptRelationEntity extends ValueObjectJpaEntity {

  /// 主题词 UI（关联：cat_mesh_descriptor.ui）
  @Column(name = "descriptor_ui", nullable = false, length = 10)
  private String descriptorUi;

  /// 所属概念 UI（拥有此关系列表的概念）
  @Column(name = "concept_ui", nullable = false, length = 10)
  private String conceptUi;

  /// 所属概念是否为首选概念
  @Column(name = "is_preferred", nullable = false)
  private Boolean isPreferred;

  /// 关系类型（NRW=Narrower/BRD=Broader/REL=Related，DTD #IMPLIED 可为 null）
  @Column(name = "relation_name", length = 10)
  private String relationName;

  /// 概念 1 UI（DTD 定义总是首选概念）
  @Column(name = "concept1_ui", nullable = false, length = 10)
  private String concept1Ui;

  /// 概念 2 UI（关联概念）
  @Column(name = "concept2_ui", nullable = false, length = 10)
  private String concept2Ui;
}
