package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// MeSH 树形编号表数据库实体，映射到表 `cat_mesh_tree_number`。
///
/// 表结构：存储 MeSH 主题词的树形编号，支持多位置和层次查询（一个主题词平均 2.3 个位置）。
///
/// 关键字段说明：
///
/// - `descriptor_ui` - 主题词 UI（格式：D000001）
///   - `tree_number` - 树形编号（如 C04.557.337.428），唯一约束 uk_tree_number
///   - `tree_level` - 层级深度（1-10，自动计算）
///   - `is_primary` - 是否主要位置（0=次要，1=主要）
///
/// 索引说明：
///
/// - uk_tree_number - 树形编号唯一索引，保证编号唯一性
///   - idx_descriptor_ui - 主题词索引，支持查询某主题词的所有位置
///   - idx_tree_prefix - 树形编号前缀索引（20 字符），支持层次查询（LIKE "D12.%"）
///   - idx_tree_level - 层级+主题词复合索引，支持按层级筛选
///
/// @author linqibin
/// @since 0.1.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_mesh_tree_number", autoResultMap = true)
public class MeshTreeNumberDO extends BaseDO {
  /// 主题词 UI（格式：D000001）
  @TableField("descriptor_ui")
  private String descriptorUi;

  /// 树形编号（如 C04.557.337.428）
  @TableField("tree_number")
  private String treeNumber;

  /// 层级深度（1-10，自动计算）
  @TableField("tree_level")
  private Integer treeLevel;

  /// 是否主要位置（0=次要，1=主要）
  @TableField("is_primary")
  private Boolean isPrimary;
}
