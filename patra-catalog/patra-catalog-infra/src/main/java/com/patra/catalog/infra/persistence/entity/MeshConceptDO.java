package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.EqualsAndHashCode;
import com.patra.starter.mybatis.entity.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

/// MeSH 概念表数据库实体，映射到表 `cat_mesh_concept`。
/// 
/// 表结构：存储 MeSH 主题词下的概念，支持概念级别的关联和检索。
/// 
/// 关键字段说明：
/// 
/// - `descriptor_id` - 主题词 ID（外键：cat_mesh_descriptor.id）
///   - `concept_ui` - 概念唯一标识符（格式：M000001-M999999），唯一约束 uk_concept_ui
///   - `concept_name` - 概念名称
///   - `is_preferred` - 是否首选概念（0=否，1=是）
///   - `casn1_name` - CAS 类型 1 名称（化学物质专用）
///   - `registry_number` - 注册号（如 CAS 号，EC 号）
///   - `scope_note` - 范围说明，TEXT 类型
///   - `concept_status` - 概念状态（枚举值）
/// 
/// 索引说明：
/// 
/// - uk_concept_ui - 概念 UI 唯一索引，支持精确查询
///   - idx_descriptor - 主题词索引，支持查询某主题词的所有概念
///   - idx_registry_number - 注册号索引，支持化学物质查询
/// 
/// @author linqibin
/// @since 0.2.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_mesh_concept", autoResultMap = true)
public class MeshConceptDO extends BaseDO {
  /// 主题词 ID（外键：cat_mesh_descriptor.id）
  @TableField("descriptor_id")
  private Long descriptorId;

  /// 概念唯一标识符（格式：M000001-M999999）
  @TableField("concept_ui")
  private String conceptUi;

  /// 概念名称
  @TableField("concept_name")
  private String conceptName;

  /// 是否首选概念（0=否，1=是）
  @TableField("is_preferred")
  private Boolean isPreferred;

  /// CAS 类型 1 名称（化学物质专用）
  @TableField("casn1_name")
  private String casn1Name;

  /// 注册号（如 CAS 号，EC 号）
  @TableField("registry_number")
  private String registryNumber;

  /// 范围说明
  @TableField("scope_note")
  private String scopeNote;

  /// 概念状态（枚举值）
  @TableField("concept_status")
  private String conceptStatus;

}
