package com.patra.registry.infra.persistence.entity.expr;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Persistence entity mapped to {@code reg_prov_expr_render_rule}.
 * <p>Defines how expression atoms are rendered into provider queries or parameters.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("reg_prov_expr_render_rule")
public class RegProvExprRenderRuleDO extends BaseDO {

    /**
     * Foreign key referencing {@code reg_provenance.id}.
     */
    @TableField("provenance_id")
    private Long provenanceId;

    /**
     * Operation type discriminator governing rule applicability.
     */
    @TableField("operation_type")
    private String operationType;

    /**
     * Lifecycle status flag for the render rule.
     */
    @TableField("lifecycle_status_code")
    private String lifecycleStatusCode;

    /**
     * Canonical field key that the rule renders.
     */
    @TableField("field_key")
    private String fieldKey;

    /**
     * Operator code (TERM/IN/RANGE/etc.).
     */
    @TableField("op_code")
    private String opCode;

    /**
     * Optional match type refinement (e.g., PHRASE/EXACT).
     */
    @TableField("match_type_code")
    private String matchTypeCode;

    /**
     * {@code true} if the rule is specific to negated expressions.
     */
    @TableField("negated")
    private Boolean negated;

    /**
     * Optional value type discriminator (STRING/DATE/DATETIME/NUMBER).
     */
    @TableField("value_type_code")
    private String valueTypeCode;

    /**
     * Render output type (QUERY or PARAMS).
     */
    @TableField("emit_type_code")
    private String emitTypeCode;

    /**
     * Normalized match type key ({@code ANY} when unspecified).
     */
    @TableField("match_type_key")
    private String matchTypeKey;

    /**
     * Normalized negation key (T/F/ANY).
     */
    @TableField("negated_key")
    private String negatedKey;

    /**
     * Normalized value type key ({@code ANY} when unspecified).
     */
    @TableField("value_type_key")
    private String valueTypeKey;

    /**
     * Inclusive timestamp when the render rule becomes effective.
     */
    @TableField("effective_from")
    private Instant effectiveFrom;

    /**
     * Exclusive timestamp when the render rule expires.
     */
    @TableField("effective_to")
    private Instant effectiveTo;

    /**
     * Template used when emitting query fragments.
     */
    @TableField("template")
    private String template;

    /**
     * Optional template applied per item for IN operators.
     */
    @TableField("item_template")
    private String itemTemplate;

    /**
     * Joiner used to concatenate multiple rendered items.
     */
    @TableField("joiner")
    private String joiner;

    /**
     * Flag indicating whether the rendered output should be wrapped in parentheses.
     */
    @TableField("wrap_group")
    private Boolean wrapGroup;

    /**
     * JSON structure containing emitted parameter mappings.
     */
    @TableField("params")
    private JsonNode params;

    /**
     * Optional custom function applied during rendering.
     */
    @TableField("fn_code")
    private String fnCode;
}
