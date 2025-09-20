package com.patra.registry.infra.persistence.entity.provenance;

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
 * 数据表 {@code reg_prov_retry_cfg} 对应的实体。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("reg_prov_retry_cfg")
public class RegProvRetryCfgDO extends BaseDO {

    @TableField("provenance_id")
    private Long provenanceId;

    @TableField("scope_code")
    private String scopeCode;

    @TableField("task_type")
    private String taskType;

    @TableField("effective_from")
    private Instant effectiveFrom;

    @TableField("effective_to")
    private Instant effectiveTo;

    @TableField("max_retry_times")
    private Integer maxRetryTimes;

    @TableField("backoff_policy_type_code")
    private String backoffPolicyTypeCode;

    @TableField("initial_delay_millis")
    private Integer initialDelayMillis;

    @TableField("max_delay_millis")
    private Integer maxDelayMillis;

    @TableField("exp_multiplier_value")
    private Double expMultiplierValue;

    @TableField("jitter_factor_ratio")
    private Double jitterFactorRatio;

    @TableField("retry_http_status_json")
    private String retryHttpStatusJson;

    @TableField("giveup_http_status_json")
    private String giveupHttpStatusJson;

    @TableField("retry_on_network_error")
    private Boolean retryOnNetworkError;

    @TableField("circuit_break_threshold")
    private Integer circuitBreakThreshold;

    @TableField("circuit_cooldown_millis")
    private Integer circuitCooldownMillis;

    @TableField("task_type_key")
    private String taskTypeKey;

    @TableField("lifecycle_status_code")
    private String lifecycleStatusCode;
}
