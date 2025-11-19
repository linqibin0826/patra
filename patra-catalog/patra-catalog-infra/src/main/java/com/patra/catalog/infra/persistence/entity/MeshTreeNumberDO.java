package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.EqualsAndHashCode;
import com.patra.starter.mybatis.entity.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

/**
 * MeSH 树形编号表数据库实体，映射到表 {@code cat_mesh_tree_number}。
 *
 * <p>表结构：存储 MeSH 主题词的树形编号，支持多位置和层次查询（一个主题词平均 2.3 个位置）。
 *
 * <p>关键字段说明：
 *
 * <ul>
 *   <li>{@code descriptor_id} - 主题词 ID（外键：cat_mesh_descriptor.id）
 *   <li>{@code tree_number} - 树形编号（如 C04.557.337.428），唯一约束 uk_tree_number
 *   <li>{@code tree_level} - 层级深度（1-10，自动计算）
 *   <li>{@code is_primary} - 是否主要位置（0=次要，1=主要）
 * </ul>
 *
 * <p>索引说明：
 *
 * <ul>
 *   <li>uk_tree_number - 树形编号唯一索引，保证编号唯一性
 *   <li>idx_descriptor - 主题词索引，支持查询某主题词的所有位置
 *   <li>idx_tree_prefix - 树形编号前缀索引（20 字符），支持层次查询（LIKE "D12.%"）
 *   <li>idx_tree_level - 层级+主题词复合索引，支持按层级筛选
 * </ul>
 *
 *
 * @author linqibin
 * @since 0.2.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_mesh_tree_number", autoResultMap = true)
public class MeshTreeNumberDO extends BaseDO {
  /** 主题词 ID（外键：cat_mesh_descriptor.id） */
  @TableField("descriptor_id")
  private Long descriptorId;

  /** 树形编号（如 C04.557.337.428） */
  @TableField("tree_number")
  private String treeNumber;

  /** 层级深度（1-10，自动计算） */
  @TableField("tree_level")
  private Integer treeLevel;

  /** 是否主要位置（0=次要，1=主要） */
  @TableField("is_primary")
  private Boolean isPrimary;

}
