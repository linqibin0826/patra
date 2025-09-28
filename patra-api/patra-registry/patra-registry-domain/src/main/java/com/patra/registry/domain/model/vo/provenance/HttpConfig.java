package com.patra.registry.domain.model.vo.provenance;

import com.patra.registry.domain.exception.DomainValidationException;
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
        DomainValidationException.positive(id, "HTTP config id");
        DomainValidationException.positive(provenanceId, "Provenance id");
        String scopeTrimmed = DomainValidationException.notBlank(scopeCode, "Scope code");
        String retryAfterTrimmed = DomainValidationException.notBlank(retryAfterPolicyCode, "Retry-after policy code");
        DomainValidationException.nonNull(effectiveFrom, "Effective from");

        this.id = id; // 已验证
        this.provenanceId = provenanceId; // 已验证
        this.scopeCode = scopeTrimmed;
        this.taskType = taskType != null ? taskType.trim() : null;
        this.taskTypeKey = taskTypeKey != null ? taskTypeKey.trim() : "ALL";
        this.effectiveFrom = effectiveFrom; // 非 null 已验证
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
        this.retryAfterPolicyCode = retryAfterTrimmed;
        this.retryAfterCapMillis = retryAfterCapMillis;
        this.idempotencyHeaderName = idempotencyHeaderName != null ? idempotencyHeaderName.trim() : null;
        this.idempotencyTtlSeconds = idempotencyTtlSeconds;
    }
}
