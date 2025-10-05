package com.patra.registry.domain.model.read.provenance;

import com.patra.registry.domain.exception.DomainValidationException;

import java.time.Instant;

/**
 * HTTP 策略配置查询视图。
 */
public record HttpConfigQuery(
        Long id,
        Long provenanceId,
        String operationType,
        Instant effectiveFrom,
        Instant effectiveTo,
        String defaultHeadersJson,
        Integer timeoutConnectMillis,
        Integer timeoutReadMillis,
        Integer timeoutTotalMillis,
        boolean tlsVerifyEnabled,
        String proxyUrlValue,
        String retryAfterPolicyCode,
        Integer retryAfterCapMillis,
        String idempotencyHeaderName,
        Integer idempotencyTtlSeconds
) {
    public HttpConfigQuery {
        if (id == null || id <= 0) {
            throw new DomainValidationException("HTTP config id must be positive");
        }
        if (provenanceId == null || provenanceId <= 0) {
            throw new DomainValidationException("Provenance id must be positive");
        }
        if (retryAfterPolicyCode == null || retryAfterPolicyCode.isBlank()) {
            throw new DomainValidationException("Retry-after policy code cannot be blank");
        }
        if (effectiveFrom == null) {
            throw new DomainValidationException("Effective from cannot be null");
        }
        operationType = operationType != null ? operationType.trim() : null;
        proxyUrlValue = proxyUrlValue != null ? proxyUrlValue.trim() : null;
        retryAfterPolicyCode = retryAfterPolicyCode.trim();
        idempotencyHeaderName = idempotencyHeaderName != null ? idempotencyHeaderName.trim() : null;
    }
}
