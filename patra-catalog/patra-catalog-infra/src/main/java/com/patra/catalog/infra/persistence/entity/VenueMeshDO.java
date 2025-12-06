package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// 载体 MeSH 主题词数据库实体，映射到表 `cat_venue_mesh`。
///
/// 表结构：存储期刊的 MeSH 主题词分类信息，来源于 NLM Serfile。
/// 与 cat_publication_mesh 命名风格保持一致。
///
/// 关键字段说明：
///
/// - `venue_id` 载体 ID，外键关联 cat_venue.id
/// - `descriptor_name` MeSH 描述符名称，如 "Medicine", "Cardiology"
/// - `descriptor_ui` MeSH 描述符唯一标识符，格式 D000001
/// - `is_major_topic` 是否主要主题，用于检索权重
/// - `qualifier_name` 限定符名称，如 "methods", "diagnosis"
/// - `qualifier_ui` 限定符唯一标识符，格式 Q000001
///
/// 索引说明：
///
/// - 唯一索引 `uk_venue_mesh`: (venue_id, descriptor_name) 防止重复
/// - 普通索引 `idx_venue_id`: venue_id 支持查询期刊的所有主题词
/// - 普通索引 `idx_descriptor_ui`: descriptor_ui 支持按 MeSH ID 反查期刊
///
/// 使用场景：
///
/// 1. 从 Serfile 导入期刊的 MeshHeadingList 数据
/// 2. 按学科分类检索期刊
/// 3. 期刊主题分析和推荐
///
/// @author linqibin
/// @since 0.1.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_venue_mesh")
public class VenueMeshDO extends BaseDO {

  /// 载体 ID（外键：cat_venue.id）
  @TableField("venue_id")
  private Long venueId;

  /// MeSH 描述符名称
  @TableField("descriptor_name")
  private String descriptorName;

  /// MeSH 描述符唯一标识符（格式：D000001）
  @TableField("descriptor_ui")
  private String descriptorUi;

  /// 是否主要主题（0=否，1=是）
  @TableField("is_major_topic")
  private Boolean isMajorTopic;

  /// MeSH 限定符名称
  @TableField("qualifier_name")
  private String qualifierName;

  /// MeSH 限定符唯一标识符（格式：Q000001）
  @TableField("qualifier_ui")
  private String qualifierUi;
}
