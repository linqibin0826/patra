package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// MeSH 概念关系表数据库实体，映射到表 `cat_mesh_concept_relation`。
///
/// 表结构：存储概念关系（ConceptRelationList），记录同一主题词内不同概念之间的语义关系。
///
/// 关键字段说明：
///
/// - `descriptor_ui` - 主题词 UI（格式：D000001）
///   - `concept_ui` - 所属概念 UI（拥有此关系列表的概念）
///   - `is_preferred` - 所属概念是否为首选概念
///   - `relation_name` - 关系类型（NRW=Narrower/BRD=Broader/REL=Related，可为 null）
///   - `concept1_ui` - 概念1 UI（DTD 定义总是首选概念）
///   - `concept2_ui` - 概念2 UI（关联概念）
///
/// 索引说明：
///
/// - idx_descriptor_ui - 主题词索引，支持查询某主题词的所有概念关系
///   - idx_concept - 概念索引，支持查询某概念的所有关系
///
/// @author linqibin
/// @since 0.2.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_mesh_concept_relation", autoResultMap = true)
public class MeshConceptRelationDO extends BaseDO {

  /// 主题词 UI（格式：D000001）
  @TableField("descriptor_ui")
  private String descriptorUi;

  /// 所属概念 UI（拥有此关系列表的概念）
  @TableField("concept_ui")
  private String conceptUi;

  /// 所属概念是否为首选概念
  @TableField("is_preferred")
  private Boolean isPreferred;

  /// 关系类型（NRW/BRD/REL，DTD #IMPLIED 可为 null）
  @TableField("relation_name")
  private String relationName;

  /// 概念1 UI（DTD 定义总是首选概念）
  @TableField("concept1_ui")
  private String concept1Ui;

  /// 概念2 UI（关联概念）
  @TableField("concept2_ui")
  private String concept2Ui;
}
