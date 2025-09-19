package com.patra.registry.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Dictionary item entity for sys_dict_item table.
 * Represents individual dictionary entries belonging to a specific dictionary type.
 * This entity is used exclusively for read-only operations in the dictionary CQRS query pipeline.
 * 
 * Business rules enforced at database level:
 * - Each type can have at most one default item (enforced by default_key generated column)
 * - Item codes must be unique within the same type
 * - Item codes follow uppercase with underscores format (e.g., "GET", "PAGE_NUMBER")
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

    /**
     * Parent dictionary type ID.
     * Logical foreign key reference to sys_dict_type.id.
     * Establishes the hierarchical relationship between types and items.
     */
    @TableField("type_id")
    private Long typeId;

    /**
     * Dictionary item code - stable business key.
     * Format: uppercase with underscores (e.g., "GET", "POST", "PAGE_NUMBER").
     * This field serves as the primary business identifier for dictionary items.
     * Must be unique within the same dictionary type.
     */
    @TableField("item_code")
    private String itemCode;

    /**
     * Human-readable item name for display purposes.
     * Used in UI components, dropdown lists, and user-facing documentation.
     * Provides clear identification of the dictionary item's meaning.
     */
    @TableField("item_name")
    private String itemName;

    /**
     * Short name or abbreviation for compact UI scenarios.
     * Optional field used when space is limited in user interfaces.
     * Provides a concise representation of the dictionary item.
     */
    @TableField("short_name")
    private String shortName;

    /**
     * Detailed description of the dictionary item.
     * Explains the semantics, boundaries, and compatibility considerations.
     * Helps developers and users understand when to use this dictionary value.
     */
    @TableField("description")
    private String description;

    /**
     * Display order for sorting dictionary items.
     * Lower values appear first in ordered lists and UI components.
     * Default value is 100, allowing for flexible insertion of new items.
     */
    @TableField("display_order")
    private Integer displayOrder;

    /**
     * Whether this item is the default value for its dictionary type.
     * Only one item per type can be marked as default (enforced by database constraint).
     * Used for providing fallback values in business logic.
     */
    @TableField("is_default")
    private Boolean isDefault;

    /**
     * Whether this dictionary item is currently enabled.
     * Disabled items (0) do not participate in business operations.
     * Enabled items (1) are available for selection and validation.
     * Allows temporary deactivation without deletion.
     */
    @TableField("enabled")
    private Boolean enabled;

    /**
     * Label color for UI display purposes.
     * Can be hex color codes (#AABBCC) or semantic color names.
     * Used by frontend components for visual differentiation.
     */
    @TableField("label_color")
    private String labelColor;

    /**
     * Icon name for UI display purposes.
     * References icon identifiers used by frontend icon libraries.
     * Provides visual representation of dictionary items in user interfaces.
     */
    @TableField("icon_name")
    private String iconName;

    /**
     * Extended attributes stored as JSON.
     * Contains business-specific key-value pairs such as aliases, hints, compatibility flags, etc.
     * Uses Jackson JsonNode for flexible JSON handling without strict schema constraints.
     */
    @TableField("attributes_json")
    private JsonNode attributesJson;

    /**
     * Generated column for enforcing unique default constraint.
     * Automatically calculated as type_id when (is_default=1 AND enabled=1 AND deleted=0), otherwise NULL.
     * Used by database unique constraint to ensure only one default item per type.
     * This field should not be manually set - it's managed by the database.
     */
    @TableField("default_key")
    private Long defaultKey;
}