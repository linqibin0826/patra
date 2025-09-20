package com.patra.registry.domain.model.vo.provenance;

import java.time.Instant;

/**
 * {@code reg_prov_http_cfg} 的领域值对象。
 */
public record HttpConfig(
        Long id,
        Long provenanceId,
        String scopeCode,
        String taskType,
        String taskTypeKey,
        Instant effectiveFrom,
        Instant effectiveTo,
        String baseUrlOverride,
        String defaultHeadersJson,
        Integer timeoutConnectMillis,
        Integer timeoutReadMillis,
        Integer timeoutTotalMillis,
        boolean tlsVerifyEnabled,
        String proxyUrlValue,
        boolean acceptCompressEnabled,
        boolean preferHttp2Enabled,
        String retryAfterPolicyCode,
        Integer retryAfterCapMillis,
        String idempotencyHeaderName,
        Integer idempotencyTtlSeconds
) {
    public HttpConfig(Long id,
                      Long provenanceId,
                      String scopeCode,
                      String taskType,
                      String taskTypeKey,
                      Instant effectiveFrom,
                      Instant effectiveTo,
                      String baseUrlOverride,
                      String defaultHeadersJson,
                      Integer timeoutConnectMillis,
                      Integer timeoutReadMillis,
                      Integer timeoutTotalMillis,
                      boolean tlsVerifyEnabled,
                      String proxyUrlValue,
                      boolean acceptCompressEnabled,
                      boolean preferHttp2Enabled,
                      String retryAfterPolicyCode,
                      Integer retryAfterCapMillis,
                      String idempotencyHeaderName,
                      Integer idempotencyTtlSeconds) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("HTTP config id must be positive");
        }
        if (provenanceId == null || provenanceId <= 0) {
            throw new IllegalArgumentException("Provenance id must be positive");
        }
        if (scopeCode == null || scopeCode.isBlank()) {
            throw new IllegalArgumentException("Scope code cannot be blank");
        }
        if (retryAfterPolicyCode == null || retryAfterPolicyCode.isBlank()) {
            throw new IllegalArgumentException("Retry-after policy code cannot be blank");
        }
        if (effectiveFrom == null) {
            throw new IllegalArgumentException("Effective from cannot be null");
        }

        this.id = id;
        this.provenanceId = provenanceId;
        this.scopeCode = scopeCode.trim();
        this.taskType = taskType != null ? taskType.trim() : null;
        this.taskTypeKey = taskTypeKey != null ? taskTypeKey.trim() : "ALL";
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.baseUrlOverride = baseUrlOverride != null ? baseUrlOverride.trim() : null;
        this.defaultHeadersJson = defaultHeadersJson;
        this.timeoutConnectMillis = timeoutConnectMillis;
        this.timeoutReadMillis = timeoutReadMillis;
        this.timeoutTotalMillis = timeoutTotalMillis;
        this.tlsVerifyEnabled = tlsVerifyEnabled;
        this.proxyUrlValue = proxyUrlValue != null ? proxyUrlValue.trim() : null;
        this.acceptCompressEnabled = acceptCompressEnabled;
        this.preferHttp2Enabled = preferHttp2Enabled;
        this.retryAfterPolicyCode = retryAfterPolicyCode.trim();
        this.retryAfterCapMillis = retryAfterCapMillis;
        this.idempotencyHeaderName = idempotencyHeaderName != null ? idempotencyHeaderName.trim() : null;
        this.idempotencyTtlSeconds = idempotencyTtlSeconds;
    }
}
