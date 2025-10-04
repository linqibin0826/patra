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

    @TableField("operation_type")
    private String operationType;

    @TableField("effective_from")
    private Instant effectiveFrom;

    @TableField("effective_to")
    private Instant effectiveTo;

    @TableField("max_concurrent_requests")
    private Integer maxConcurrentRequests;

    @TableField("per_credential_qps_limit")
    private Integer perCredentialQpsLimit;

    @TableField("operation_type_key")
    private String operationTypeKey;

    @TableField("lifecycle_status_code")
    private String lifecycleStatusCode;
}
