package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// MeSH 组合条目表数据库实体，映射到表 `cat_mesh_entry_combination`。
///
/// 表结构：存储主题词的组合条目信息（EntryCombinationList），用于指导索引员如何标引某些复杂概念。
///
/// 关键字段说明：
///
/// - `descriptor_ui` - 主题词 UI（格式：D000001）
/// - `ecin_descriptor_ui` - ECIN Descriptor UI（输入组合的主题词）
/// - `ecin_qualifier_ui` - ECIN Qualifier UI（输入组合的限定词）
/// - `ecout_descriptor_ui` - ECOUT Descriptor UI（输出组合的主题词）
/// - `ecout_qualifier_ui` - ECOUT Qualifier UI（输出组合的限定词，可选）
///
/// 业务说明：
///
/// - ECIN (Entry Combination In): 当用户搜索此主题词+限定词时
/// - ECOUT (Entry Combination Out): 应该用这个主题词+限定词替代
/// - 例如：搜索 "Eye Diseases/drug therapy" 应该重定向到 "Eye Diseases/therapy"
///
/// 索引说明：
///
/// - idx_descriptor_ui - 主题词索引，支持查询某主题词的所有组合条目
/// - idx_ecin_descriptor - ECIN 主题词索引
/// - idx_ecout_descriptor - ECOUT 主题词索引
///
/// @author linqibin
/// @since 0.2.1
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_mesh_entry_combination", autoResultMap = true)
public class MeshEntryCombinationDO extends BaseDO {
  /// 主题词 UI（格式：D000001）
  @TableField("descriptor_ui")
  private String descriptorUi;

  /// ECIN Descriptor UI（输入组合的主题词）
  @TableField("ecin_descriptor_ui")
  private String ecinDescriptorUi;

  /// ECIN Qualifier UI（输入组合的限定词）
  @TableField("ecin_qualifier_ui")
  private String ecinQualifierUi;

  /// ECOUT Descriptor UI（输出组合的主题词）
  @TableField("ecout_descriptor_ui")
  private String ecoutDescriptorUi;

  /// ECOUT Qualifier UI（输出组合的限定词，可选）
  @TableField("ecout_qualifier_ui")
  private String ecoutQualifierUi;
}
