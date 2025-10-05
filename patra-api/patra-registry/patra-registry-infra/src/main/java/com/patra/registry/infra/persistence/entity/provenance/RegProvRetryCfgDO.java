package com.patra.registry.infra.persistence.entity.provenance;

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
 * Persistence entity mapped to {@code reg_prov_retry_cfg}.
 * <p>Contains retry and backoff rules that are applied when interacting with
 * a provenance for the specified operation type.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("reg_prov_retry_cfg")
public class RegProvRetryCfgDO extends BaseDO {

    /**
     * Foreign key referencing {@code reg_provenance.id}.
     */
    @TableField("provenance_id")
    private Long provenanceId;

    /**
     * Operation type discriminator for the retry configuration.
     */
    @TableField("operation_type")
    private String operationType;

    /**
     * Inclusive timestamp when the retry policy becomes effective.
     */
    @TableField("effective_from")
    private Instant effectiveFrom;

    /**
     * Exclusive timestamp when the retry policy expires (nullable).
     */
    @TableField("effective_to")
    private Instant effectiveTo;

    /**
     * Maximum number of retry attempts allowed.
     */
    @TableField("max_retry_times")
    private Integer maxRetryTimes;

    /**
     * Backoff policy type (e.g., FIXED/EXPONENTIAL/EXP_JITTER).
     */
    @TableField("backoff_policy_type_code")
    private String backoffPolicyTypeCode;

    /**
     * Initial delay in milliseconds before the first retry attempt.
     */
    @TableField("initial_delay_millis")
    private Integer initialDelayMillis;

    /**
     * Upper bound in milliseconds for any retry delay.
     */
    @TableField("max_delay_millis")
    private Integer maxDelayMillis;

    /**
     * Multiplier applied between successive retries when exponential backoff is used.
     */
    @TableField("exp_multiplier_value")
    private Double expMultiplierValue;

    /**
     * Fraction specifying the jitter amplitude applied to backoff delays.
     */
    @TableField("jitter_factor_ratio")
    private Double jitterFactorRatio;

    /**
     * JSON array describing HTTP status codes that should trigger a retry.
     */
    @TableField("retry_http_status_json")
    private JsonNode retryHttpStatusJson;

    /**
     * JSON array describing HTTP status codes that should skip retries.
     */
    @TableField("giveup_http_status_json")
    private JsonNode giveupHttpStatusJson;

    /**
     * Flag indicating whether network-level errors are eligible for retries.
     */
    @TableField("retry_on_network_error")
    private Boolean retryOnNetworkError;

    /**
     * Number of consecutive failures required to trip the circuit breaker.
     */
    @TableField("circuit_break_threshold")
    private Integer circuitBreakThreshold;

    /**
     * Cooldown interval in milliseconds after the circuit breaker trips.
     */
    @TableField("circuit_cooldown_millis")
    private Integer circuitCooldownMillis;

    /**
     * Lifecycle status code reflecting whether the policy is active.
     */
    @TableField("lifecycle_status_code")
    private String lifecycleStatusCode;
}
