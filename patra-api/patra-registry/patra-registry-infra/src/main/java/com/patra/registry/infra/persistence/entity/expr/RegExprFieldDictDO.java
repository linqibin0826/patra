package com.patra.registry.infra.persistence.entity.expr;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Persistence entity mapped to {@code reg_expr_field_dict}.
 * <p>Defines canonical field metadata shared across provenances.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("reg_expr_field_dict")
public class RegExprFieldDictDO extends BaseDO {

    /** Canonical field key that remains stable across environments. */
    @TableField("field_key")
    private String fieldKey;

    /** Optional display name surfaced in consoles. */
    @TableField("display_name")
    private String displayName;

    /** Rich description explaining field semantics. */
    @TableField("description")
    private String description;

    /** Data type code (DATE/DATETIME/NUMBER/TEXT/...). */
    @TableField("data_type_code")
    private String dataTypeCode;

    /** Cardinality code indicating single or multi-valued fields. */
    @TableField("cardinality_code")
    private String cardinalityCode;

    /** Flag indicating whether the field is exposable to clients. */
    @TableField("exposable")
    private Boolean exposable;

    /** Flag indicating whether the field should be treated as date-like. */
    @TableField("is_date")
    private Boolean dateField;
}
