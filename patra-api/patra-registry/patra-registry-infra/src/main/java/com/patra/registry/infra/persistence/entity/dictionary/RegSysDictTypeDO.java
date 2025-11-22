package com.patra.registry.infra.persistence.entity.dictionary;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/// 数据库实体,映射到表 `sys_dict_type`.
/// 
/// Exposes dictionary type metadata to the query side of the CQRS stack.
/// 
/// Important invariants enforced at the database layer:
/// 
/// - `type_code` is unique across all types and acts as the business key.
///   - System types (`is_system = 1`) are managed by the platform and typically immutable.
///   - Types flagged with `allow_custom_items = 1` may be extended by business users.
/// 
/// @author linqibin
/// @since 0.1.0
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_type")
public class RegSysDictTypeDO extends BaseDO {

  /// Stable business key for the dictionary type (e.g., `http_method`). Expected format is
/// lower-case snake case.
  @TableField("type_code")
  private String typeCode;

  /// Human-readable display name used by UI components.
  @TableField("type_name")
  private String typeName;

  /// 可选的 free-form description that documents usage guidelines.
  @TableField("description")
  private String description;

  /// 指示 whether business users are allowed to create custom items.
  @TableField("allow_custom_items")
  private Boolean allowCustomItems;

  /// Flag marking whether this dictionary type is managed by the platform (`true`) or business
/// (`false`).
  @TableField("is_system")
  private Boolean isSystem;

  /// 可选的 JSON payload for additional metadata (e.g., colour, icon stereotypes).
  @TableField("reserved_json")
  private JsonNode reservedJson;
}
