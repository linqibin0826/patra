package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// MeSH 限定词表数据库实体，映射到表 `cat_mesh_qualifier`。
///
/// 表结构：存储 MeSH 限定词，用于修饰主题词（如 "immunology" 限定 "Antibodies"）。
///
/// 关键字段说明：
///
/// - `ui` - 限定词唯一标识符（格式：Q000001-Q999999），唯一约束 uk_qualifier_ui
///   - `name` - 限定词名称（英文）
///   - `abbreviation` - 限定词缩写（如 DI, GE, IM）
///   - `annotation` - 注释说明，TEXT 类型
///   - `active_status` - 是否有效（0=已废弃，1=有效）
///   - `mesh_version` - MeSH 版本年份（如 "2025"）
///
/// 索引说明：
///
/// - uk_qualifier_ui - 限定词 UI 唯一索引，支持精确查询（&lt;5ms）
///   - idx_name - 限定词名称索引，支持按名称查询
///
/// @author linqibin
/// @since 0.1.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_mesh_qualifier", autoResultMap = true)
public class MeshQualifierDO extends BaseDO {
  /// 限定词唯一标识符（格式：Q000001-Q999999）
  @TableField("ui")
  private String ui;

  /// 限定词名称（英文）
  @TableField("name")
  private String name;

  /// 限定词缩写（如 DI, GE, IM）
  @TableField("abbreviation")
  private String abbreviation;

  /// 注释说明
  @TableField("annotation")
  private String annotation;

  /// 创建日期（格式：YYYYMMDD）
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

  /// 历史说明（记录限定词的历史使用规则）
  @TableField("history_note")
  private String historyNote;

  /// 在线检索说明（检索策略指南）
  @TableField("online_note")
  private String onlineNote;

  /// 树形编号列表（JSON 字符串，限定词在 MeSH 层级树中的位置）
  @TableField("tree_numbers")
  private String treeNumbers;
}
