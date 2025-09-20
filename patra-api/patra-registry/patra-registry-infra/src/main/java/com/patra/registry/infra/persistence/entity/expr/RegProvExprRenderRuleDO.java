package com.patra.registry.infra.persistence.entity.expr;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * 数据表 {@code reg_prov_expr_render_rule} 对应的实体。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("reg_prov_expr_render_rule")
public class RegProvExprRenderRuleDO extends BaseDO {

    @TableField("provenance_id")
    private Long provenanceId;

    @TableField("scope_code")
    private String scopeCode;

    @TableField("task_type")
    private String taskType;

    @TableField("task_type_key")
    private String taskTypeKey;

    @TableField("field_key")
    private String fieldKey;

    @TableField("op_code")
    private String opCode;

    @TableField("match_type_code")
    private String matchTypeCode;

    @TableField("negated")
    private Boolean negated;

    @TableField("value_type_code")
    private String valueTypeCode;

    @TableField("emit_type_code")
    private String emitTypeCode;

    @TableField("match_type_key")
    private String matchTypeKey;

    @TableField("negated_key")
    private String negatedKey;

    @TableField("value_type_key")
    private String valueTypeKey;

    @TableField("effective_from")
    private Instant effectiveFrom;

    @TableField("effective_to")
    private Instant effectiveTo;

    @TableField("template")
    private String template;

    @TableField("item_template")
    private String itemTemplate;

    @TableField("joiner")
    private String joiner;

    @TableField("wrap_group")
    private Boolean wrapGroup;

    @TableField("params")
    private String params;

    @TableField("fn_code")
    private String fnCode;
}
