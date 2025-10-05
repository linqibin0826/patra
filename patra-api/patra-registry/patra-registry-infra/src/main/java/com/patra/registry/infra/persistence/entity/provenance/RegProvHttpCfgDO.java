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
 * Persistence entity mapped to {@code reg_prov_http_cfg}.
 * <p>Holds HTTP policy overrides such as default headers and timeout
 * definitions for a provenance/operation combination.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("reg_prov_http_cfg")
public class RegProvHttpCfgDO extends BaseDO {

    /**
     * Foreign key referencing {@code reg_provenance.id}.
     */
    @TableField("provenance_id")
    private Long provenanceId;

    /**
     * Operation type discriminator (ALL/HARVEST/UPDATE/BACKFILL).
     */
    @TableField("operation_type")
    private String operationType;

    /**
     * Inclusive effective start timestamp of this HTTP policy slice.
     */
    @TableField("effective_from")
    private Instant effectiveFrom;

    /**
     * Exclusive effective end timestamp; {@code null} denotes an open-ended slice.
     */
    @TableField("effective_to")
    private Instant effectiveTo;

    /**
     * JSON payload defining default headers to merge into outgoing requests.
     */
    @TableField("default_headers_json")
    private JsonNode defaultHeadersJson;

    /**
     * Connection timeout in milliseconds (TCP/TLS handshake).
     */
    @TableField("timeout_connect_millis")
    private Integer timeoutConnectMillis;

    /**
     * Read timeout in milliseconds while downloading the response body.
     */
    @TableField("timeout_read_millis")
    private Integer timeoutReadMillis;

    /**
     * Total request timeout in milliseconds covering the entire call window.
     */
    @TableField("timeout_total_millis")
    private Integer timeoutTotalMillis;

    /**
     * Flag indicating whether TLS certificate validation is enforced.
     */
    @TableField("tls_verify_enabled")
    private Boolean tlsVerifyEnabled;

    /**
     * Optional proxy endpoint (e.g., {@code http://user:pass@host:port}).
     */
    @TableField("proxy_url_value")
    private String proxyUrlValue;

    /**
     * Strategy code describing how to interpret server {@code Retry-After} headers.
     */
    @TableField("retry_after_policy_code")
    private String retryAfterPolicyCode;

    /**
     * Maximum wait duration in milliseconds when the policy respects {@code Retry-After}.
     */
    @TableField("retry_after_cap_millis")
    private Integer retryAfterCapMillis;

    /**
     * Name of the idempotency header to inject for write operations.
     */
    @TableField("idempotency_header_name")
    private String idempotencyHeaderName;

    /**
     * Time-to-live in seconds for the idempotency key on the provider side.
     */
    @TableField("idempotency_ttl_seconds")
    private Integer idempotencyTtlSeconds;

    /**
     * Lifecycle status code marking whether the record is currently active.
     */
    @TableField("lifecycle_status_code")
    private String lifecycleStatusCode;
}
