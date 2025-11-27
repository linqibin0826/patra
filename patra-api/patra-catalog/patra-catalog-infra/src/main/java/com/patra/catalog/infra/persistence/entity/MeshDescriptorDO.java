package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// MeSH 主题词表数据库实体，映射到表 `cat_mesh_descriptor`。
///
/// 表结构：存储 NLM MeSH 主题词核心信息，医学文献标引权威词表。
///
/// 关键字段说明：
///
/// - `ui` - MeSH 唯一标识符（格式：D000001-D999999），唯一约束 uk_mesh_ui
///   - `name` - 主题词名称（首选术语，英文）
///   - `descriptor_class` - 主题词类型（枚举：1-Topical/2-PublicationType/3-Geographicals/4-CheckTag）
///   - `scope_note` - 范围说明（定义和使用指南），TEXT 类型
///   - `metadata` - 其他元数据（扩展字段），JSON 类型
///   - `active_status` - 是否有效（0=已废弃，1=有效）
///   - `mesh_version` - MeSH 版本年份（如 "2025"）
///
/// 索引说明：
///
/// - uk_mesh_ui - MeSH UI 唯一索引，支持高频精确查询（&lt;5ms）
///   - idx_name - 主题词名称索引，支持按名称查询
///   - idx_active_version - 有效状态+版本复合索引，筛选某版本的有效主题词
///
/// @author linqibin
/// @since 0.1.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_mesh_descriptor", autoResultMap = true)
public class MeshDescriptorDO extends BaseDO {
  /// MeSH 唯一标识符（格式：D000001-D999999）
  @TableField("ui")
  private String ui;

  /// 主题词名称（首选术语，英文）
  @TableField("name")
  private String name;

  /// 主题词类型（枚举：1-Topical/2-PublicationType/3-Geographicals/4-CheckTag）
  @TableField("descriptor_class")
  private String descriptorClass;

  /// 范围说明（定义和使用指南）
  @TableField("scope_note")
  private String scopeNote;

  /// 注释（索引员使用的说明）
  @TableField("annotation")
  private String annotation;

  /// 之前的索引方式（历史参考）
  @TableField("previous_indexing")
  private String previousIndexing;

  /// 公共 MeSH 注释（面向用户）
  @TableField("public_mesh_note")
  private String publicMeshNote;

  /// 另请参考（相关主题词建议）
  @TableField("consider_also")
  private String considerAlso;

  /// 历史说明（记录主题词的历史使用规则）
  @TableField("history_note")
  private String historyNote;

  /// 在线检索说明（检索策略指南）
  @TableField("online_note")
  private String onlineNote;

  /// NLM 分类号
  @TableField("nlm_classification_number")
  private String nlmClassificationNumber;

  /// 创建日期（格式：YYYYMMDD，如 20230115）
  @TableField("date_created")
  private String dateCreated;

  /// 修订日期（格式：YYYYMMDD）
  @TableField("date_revised")
  private String dateRevised;

  /// 确立日期（格式：YYYYMMDD）
  @TableField("date_established")
  private String dateEstablished;

  /// 是否有效（0=已废弃，1=有效）
  @TableField("active_status")
  private Boolean activeStatus;

  /// MeSH 版本年份（如 "2025"）
  @TableField("mesh_version")
  private String meshVersion;

  /// 其他元数据（扩展字段，JSON 格式）
  @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
  private JsonNode metadata;
}
