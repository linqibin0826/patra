package com.patra.registry.infra.persistence.entity.provenance;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Persistence entity mapped to {@code reg_prov_rate_limit_cfg}.
 * <p>Captures concurrency and credential-level throttling limits for a
 * provenance/operation combination.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("reg_prov_rate_limit_cfg")
public class RegProvRateLimitCfgDO extends BaseDO {

    /**
     * Foreign key referencing {@code reg_provenance.id}.
     */
    @TableField("provenance_id")
    private Long provenanceId;

    /**
     * Operation type discriminator controlling which executions the rule applies to.
     */
    @TableField("operation_type")
    private String operationType;

    /**
     * Inclusive start timestamp indicating when the rate limit takes effect.
     */
    @TableField("effective_from")
    private Instant effectiveFrom;

    /**
     * Exclusive end timestamp after which the rate limit no longer applies.
     */
    @TableField("effective_to")
    private Instant effectiveTo;

    /**
     * Maximum concurrent HTTP requests permitted for this provenance/operation pair.
     */
    @TableField("max_concurrent_requests")
    private Integer maxConcurrentRequests;

    /**
     * Maximum requests per second allowed per credential; {@code null} keeps engine defaults.
     */
    @TableField("per_credential_qps_limit")
    private Integer perCredentialQpsLimit;

    /**
     * Normalized operation type key (defaults to {@code ALL}).
     */
    @TableField("operation_type_key")
    private String operationTypeKey;

    /**
     * Lifecycle status code marking whether the configuration is active.
     */
    @TableField("lifecycle_status_code")
    private String lifecycleStatusCode;
}
