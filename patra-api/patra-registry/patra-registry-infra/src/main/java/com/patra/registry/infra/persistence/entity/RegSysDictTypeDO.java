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
 * Dictionary type entity for sys_dict_type table.
 * Represents dictionary type definitions with metadata for CQRS read operations.
 * This entity is used exclusively for read-only operations in the dictionary query pipeline.
 * 
 * @author linqibin
 * @since 0.1.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_type")
public class RegSysDictTypeDO extends BaseDO {

    /**
     * Dictionary type code - stable key for cross-environment identification.
     * Format: lowercase with underscores (e.g., "http_method", "endpoint_usage").
     * This field serves as the primary business identifier for dictionary types.
     */
    @TableField("type_code")
    private String typeCode;

    /**
     * Human-readable type name for display purposes.
     * Used in UI components and documentation to provide clear type identification.
     */
    @TableField("type_name")
    private String typeName;

    /**
     * Detailed description of the dictionary type.
     * Explains the purpose, usage scenarios, and boundaries of this dictionary type.
     */
    @TableField("description")
    private String description;

    /**
     * Whether custom items can be added to this dictionary type.
     * When true (1), allows business users to extend the dictionary with custom values.
     * When false (0), restricts the dictionary to system-defined values only.
     */
    @TableField("allow_custom_items")
    private Boolean allowCustomItems;

    /**
     * Whether this is a system-built-in dictionary type.
     * System types (1) are managed by the platform and should not be modified by business users.
     * Business types (0) can be customized according to specific requirements.
     */
    @TableField("is_system")
    private Boolean isSystem;

    /**
     * Extended metadata stored as JSON.
     * Contains additional configuration such as UI colors, icons, sorting strategies, etc.
     * Uses Jackson JsonNode for flexible JSON handling without strict schema constraints.
     */
    @TableField("reserved_json")
    private JsonNode reservedJson;
}