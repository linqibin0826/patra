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

/**
 * 数据库实体,映射到表 {@code sys_dict_item}.
 *
 * <p>Represents individual dictionary items that belong to a dictionary type.
 *
 * <p>Database level highlights:
 *
 * <ul>
 *   <li>Each type can have at most one default item (enforced via {@code default_key}).
 *   <li>{@code item_code} must be unique within the boundaries of {@code type_id}.
 *   <li>{@code item_code} is expected to use upper snake case (e.g., {@code GET}, {@code
 *       PAGE_NUMBER}).
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_item")
public class RegSysDictItemDO extends BaseDO {

  /** Identifier of the owning dictionary type ({@code sys_dict_type.id}). */
  @TableField("type_id")
  private Long typeId;

  /** Stable item code within the owning type. */
  @TableField("item_code")
  private String itemCode;

  /** Human-readable item name presented to end users. */
  @TableField("item_name")
  private String itemName;

  /** 可选的 short name or abbreviation for compact UI placements. */
  @TableField("short_name")
  private String shortName;

  /** 可选的 description documenting semantics and usage hints. */
  @TableField("description")
  private String description;

  /** Display order (ascending). Smaller numbers appear earlier. */
  @TableField("display_order")
  private Integer displayOrder;

  /** 指示 whether the item is the default choice for its type. */
  @TableField("is_default")
  private Boolean isDefault;

  /** Flag denoting whether the item is enabled and should be available to the business layer. */
  @TableField("enabled")
  private Boolean enabled;

  /** 可选的 label colour (either hex e.g. {@code #AABBCC} or semantic name). */
  @TableField("label_color")
  private String labelColor;

  /** 可选的 icon reference used by UI surfaces. */
  @TableField("icon_name")
  private String iconName;

  /** Extensible JSON payload for additional business attributes. */
  @TableField("attributes_json")
  private JsonNode attributesJson;

  /** Generated column used to enforce one default item per type (handled by the database). */
  @TableField("default_key")
  private Long defaultKey;
}
