package com.patra.registry.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Dictionary item alias entity for sys_dict_item_alias table.
 * Provides external system mappings for dictionary items to support legacy system integration.
 * This entity is used exclusively for read-only operations in the dictionary CQRS query pipeline.
 * 
 * Business rules:
 * - Each (source_system, external_code) combination must be globally unique
 * - Aliases enable integration with external systems without modifying business tables
 * - Common source systems include: pubmed, crossref, legacy_v1, etc.
 * 
 * @author linqibin
 * @since 0.1.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_item_alias")
public class RegSysDictItemAliasDO extends BaseDO {

    /**
     * Dictionary item ID that this alias maps to.
     * Logical foreign key reference to sys_dict_item.id.
     * Establishes the relationship between external codes and internal dictionary items.
     */
    @TableField("item_id")
    private Long itemId;

    /**
     * Source system identifier for the external mapping.
     * Identifies which external system or legacy application provides this alias.
     * Format: lowercase with underscores or hyphens (e.g., "pubmed", "crossref", "legacy_v1").
     * Used to namespace external codes and prevent conflicts between systems.
     */
    @TableField("source_system")
    private String sourceSystem;

    /**
     * External code or value from the source system.
     * The actual code/value used by the external system to represent this dictionary item.
     * Combined with source_system, this must be globally unique across all aliases.
     * Used as the mapping key for translating external values to internal dictionary items.
     */
    @TableField("external_code")
    private String externalCode;

    /**
     * External label or display name from the source system.
     * Optional field containing the human-readable name used by the external system.
     * Provides context for understanding the external system's terminology.
     */
    @TableField("external_label")
    private String externalLabel;

    /**
     * Notes about the alias mapping.
     * Contains additional information such as differences, compatibility notes, source links, etc.
     * Helps developers understand the mapping context and any special considerations.
     */
    @TableField("notes")
    private String notes;
}