package com.patra.registry.domain.model.read.provenance;

import java.time.Instant;

/**
 * HTTP 策略配置查询视图。
 */
public record HttpConfigQuery(
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
    public HttpConfigQuery {
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
        scopeCode = scopeCode.trim();
        taskType = taskType != null ? taskType.trim() : null;
        taskTypeKey = taskTypeKey != null ? taskTypeKey.trim() : "ALL";
        baseUrlOverride = baseUrlOverride != null ? baseUrlOverride.trim() : null;
        defaultHeadersJson = defaultHeadersJson;
        proxyUrlValue = proxyUrlValue != null ? proxyUrlValue.trim() : null;
        retryAfterPolicyCode = retryAfterPolicyCode.trim();
        idempotencyHeaderName = idempotencyHeaderName != null ? idempotencyHeaderName.trim() : null;
    }
}
