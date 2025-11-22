package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// MeSH 入口术语表数据库实体，映射到表 `cat_mesh_entry_term`。
///
/// 表结构：存储 MeSH 主题词的同义词和入口术语，支持模糊检索（如 "A-23187" → "Calcimycin"）。
///
/// 关键字段说明：
///
/// - `descriptor_id` - 主题词 ID（外键：cat_mesh_descriptor.id）
///   - `term` - 入口术语/同义词
///   - `lexical_tag` - 词法标记（枚举：NON/PEF/LAB/ABB/ACR/NAM）
///   - `is_print_flag` - 是否打印（0=否，1=是）
///   - `record_preferred` - 记录首选（枚举：Y/N）
///   - `is_permuted_term` - 是否排列术语（0=否，1=是）
///
/// 索引说明：
///
/// - idx_descriptor - 主题词索引，支持查询某主题词的所有入口术语
///
/// @author linqibin
/// @since 0.1.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_mesh_entry_term", autoResultMap = true)
public class MeshEntryTermDO extends BaseDO {
  /// 主题词 ID（外键：cat_mesh_descriptor.id）
  @TableField("descriptor_id")
  private Long descriptorId;

  /// 入口术语/同义词
  @TableField("term")
  private String term;

  /// 词法标记（枚举：NON/PEF/LAB/ABB/ACR/NAM）
  @TableField("lexical_tag")
  private String lexicalTag;

  /// 是否打印（0=否，1=是）
  @TableField("is_print_flag")
  private Boolean isPrintFlag;

  /// 记录首选（枚举：Y/N）
  @TableField("record_preferred")
  private String recordPreferred;

  /// 是否排列术语（0=否，1=是）
  @TableField("is_permuted_term")
  private Boolean isPermutedTerm;
}
