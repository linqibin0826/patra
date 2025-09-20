package com.patra.registry.infra.persistence.entity.expr;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 数据表 {@code reg_prov_expr_capability} 对应的实体。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("reg_prov_expr_capability")
public class RegProvExprCapabilityDO extends BaseDO {

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

    @TableField("effective_from")
    private Instant effectiveFrom;

    @TableField("effective_to")
    private Instant effectiveTo;

    @TableField("ops")
    private String ops;

    @TableField("negatable_ops")
    private String negatableOps;

    @TableField("supports_not")
    private Boolean supportsNot;

    @TableField("term_matches")
    private String termMatches;

    @TableField("term_case_sensitive_allowed")
    private Boolean termCaseSensitiveAllowed;

    @TableField("term_allow_blank")
    private Boolean termAllowBlank;

    @TableField("term_min_len")
    private Integer termMinLen;

    @TableField("term_max_len")
    private Integer termMaxLen;

    @TableField("term_pattern")
    private String termPattern;

    @TableField("in_max_size")
    private Integer inMaxSize;

    @TableField("in_case_sensitive_allowed")
    private Boolean inCaseSensitiveAllowed;

    @TableField("range_kind_code")
    private String rangeKindCode;

    @TableField("range_allow_open_start")
    private Boolean rangeAllowOpenStart;

    @TableField("range_allow_open_end")
    private Boolean rangeAllowOpenEnd;

    @TableField("range_allow_closed_at_infty")
    private Boolean rangeAllowClosedAtInfty;

    @TableField("date_min")
    private LocalDate dateMin;

    @TableField("date_max")
    private LocalDate dateMax;

    @TableField("datetime_min")
    private Instant datetimeMin;

    @TableField("datetime_max")
    private Instant datetimeMax;

    @TableField("number_min")
    private BigDecimal numberMin;

    @TableField("number_max")
    private BigDecimal numberMax;

    @TableField("exists_supported")
    private Boolean existsSupported;

    @TableField("token_kinds")
    private String tokenKinds;

    @TableField("token_value_pattern")
    private String tokenValuePattern;
}
