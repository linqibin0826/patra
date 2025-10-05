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
 * Persistence entity mapped to {@code reg_prov_api_param_map}.
 * <p>Tracks provider-specific parameter names for standardized query keys.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("reg_prov_api_param_map")
public class RegProvApiParamMapDO extends BaseDO {

    /**
     * Foreign key referencing {@code reg_provenance.id}.
     */
    @TableField("provenance_id")
    private Long provenanceId;

    /**
     * Operation type discriminator associated with the mapping.
     */
    @TableField("operation_type")
    private String operationType;

    /**
     * Lifecycle status code for the mapping record.
     */
    @TableField("lifecycle_status_code")
    private String lifecycleStatusCode;

    /**
     * Endpoint name this mapping applies to; {@code null} means all endpoints.
     */
    @TableField("endpoint_name")
    private String endpointName;

    /**
     * Standardized key resolved by expression rendering (e.g., {@code from}, {@code term}).
     */
    @TableField("std_key")
    private String stdKey;

    /**
     * Provider-specific parameter name aligned with the standard key.
     */
    @TableField("provider_param_name")
    private String providerParamName;

    /**
     * Optional transform applied to the value before sending to the provider.
     */
    @TableField("transform_code")
    private String transformCode;

    /**
     * Free-form JSON notes describing special handling.
     */
    @TableField("notes")
    private JsonNode notes;

    /**
     * Inclusive timestamp indicating when the mapping becomes effective.
     */
    @TableField("effective_from")
    private Instant effectiveFrom;

    /**
     * Exclusive timestamp indicating when the mapping expires.
     */
    @TableField("effective_to")
    private Instant effectiveTo;
}
