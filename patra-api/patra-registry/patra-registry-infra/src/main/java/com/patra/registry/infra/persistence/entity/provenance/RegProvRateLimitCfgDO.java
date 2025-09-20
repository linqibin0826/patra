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
 * 数据表 {@code reg_prov_rate_limit_cfg} 对应的实体。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("reg_prov_rate_limit_cfg")
public class RegProvRateLimitCfgDO extends BaseDO {

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

    @TableField("rate_tokens_per_second")
    private Integer rateTokensPerSecond;

    @TableField("burst_bucket_capacity")
    private Integer burstBucketCapacity;

    @TableField("max_concurrent_requests")
    private Integer maxConcurrentRequests;

    @TableField("per_credential_qps_limit")
    private Integer perCredentialQpsLimit;

    @TableField("bucket_granularity_scope_code")
    private String bucketGranularityScopeCode;

    @TableField("smoothing_window_millis")
    private Integer smoothingWindowMillis;

    @TableField("respect_server_rate_header")
    private Boolean respectServerRateHeader;

    @TableField("endpoint_id")
    private Long endpointId;

    @TableField("credential_name")
    private String credentialName;

    @TableField("task_type_key")
    private String taskTypeKey;

    @TableField("lifecycle_status_code")
    private String lifecycleStatusCode;
}
