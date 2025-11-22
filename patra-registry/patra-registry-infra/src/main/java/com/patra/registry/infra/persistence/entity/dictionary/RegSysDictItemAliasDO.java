package com.patra.registry.infra.persistence.entity.dictionary;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/// 数据库实体,映射到表 `sys_dict_item_alias`.
///
/// Provides external aliases so that partner systems can reference internal dictionary items.
///
/// Database rules:
///
/// - `(source_system, external_code)` is globally unique.
///   - Aliases are soft-deletable and may coexist with disabled dictionary items.
///
/// @author linqibin
/// @since 0.1.0
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_item_alias")
public class RegSysDictItemAliasDO extends BaseDO {

  /// Identifier of the dictionary item this alias resolves to (`sys_dict_item.id`).
  @TableField("item_id")
  private Long itemId;

  /// External system identifier (e.g., `pubmed`, `crossref`).
  @TableField("source_system")
  private String sourceSystem;

  /// External code provided by the upstream system for the referenced item.
  @TableField("external_code")
  private String externalCode;

  /// 可选的 human-readable label coming from the upstream system.
  @TableField("external_label")
  private String externalLabel;

  /// 可选的 notes describing the mapping context or any special behaviour.
  @TableField("notes")
  private String notes;
}
