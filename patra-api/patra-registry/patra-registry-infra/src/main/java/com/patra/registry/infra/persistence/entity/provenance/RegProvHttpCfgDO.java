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
 * 数据表 {@code reg_prov_http_cfg} 对应的实体。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("reg_prov_http_cfg")
public class RegProvHttpCfgDO extends BaseDO {

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

    @TableField("base_url_override")
    private String baseUrlOverride;

    @TableField("default_headers_json")
    private String defaultHeadersJson;

    @TableField("timeout_connect_millis")
    private Integer timeoutConnectMillis;

    @TableField("timeout_read_millis")
    private Integer timeoutReadMillis;

    @TableField("timeout_total_millis")
    private Integer timeoutTotalMillis;

    @TableField("tls_verify_enabled")
    private Boolean tlsVerifyEnabled;

    @TableField("proxy_url_value")
    private String proxyUrlValue;

    @TableField("accept_compress_enabled")
    private Boolean acceptCompressEnabled;

    @TableField("prefer_http2_enabled")
    private Boolean preferHttp2Enabled;

    @TableField("retry_after_policy_code")
    private String retryAfterPolicyCode;

    @TableField("retry_after_cap_millis")
    private Integer retryAfterCapMillis;

    @TableField("idempotency_header_name")
    private String idempotencyHeaderName;

    @TableField("idempotency_ttl_seconds")
    private Integer idempotencyTtlSeconds;

    @TableField("task_type_key")
    private String taskTypeKey;

    @TableField("lifecycle_status_code")
    private String lifecycleStatusCode;
}
