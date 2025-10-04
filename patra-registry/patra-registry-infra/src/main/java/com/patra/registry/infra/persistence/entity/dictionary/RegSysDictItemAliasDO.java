package com.patra.registry.infra.persistence.entity.dictionary;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Persistence entity mapped to {@code sys_dict_item_alias}.
 * <p>Provides external aliases so that partner systems can reference internal dictionary items.</p>
 *
 * <p>Database rules:</p>
 * <ul>
 *   <li>{@code (source_system, external_code)} is globally unique.</li>
 *   <li>Aliases are soft-deletable and may coexist with disabled dictionary items.</li>
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
@TableName("sys_dict_item_alias")
public class RegSysDictItemAliasDO extends BaseDO {

    /**
     * Identifier of the dictionary item this alias resolves to ({@code sys_dict_item.id}).
     */
    @TableField("item_id")
    private Long itemId;

    /**
     * External system identifier (e.g., {@code pubmed}, {@code crossref}).
     */
    @TableField("source_system")
    private String sourceSystem;

    /**
     * External code provided by the upstream system for the referenced item.
     */
    @TableField("external_code")
    private String externalCode;

    /**
     * Optional human-readable label coming from the upstream system.
     */
    @TableField("external_label")
    private String externalLabel;

    /**
     * Optional notes describing the mapping context or any special behaviour.
     */
    @TableField("notes")
    private String notes;
}
