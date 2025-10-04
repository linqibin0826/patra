package com.patra.registry.domain.model.vo.provenance;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;

/**
 * {@code reg_prov_http_cfg} 的领域值对象。
 */
public record HttpConfig(
        Long id,
        Long provenanceId,
        String operationType,
        String operationTypeKey,
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
    public HttpConfig(Long id,
                      Long provenanceId,
                      String operationType,
                      String operationTypeKey,
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
                      Integer idempotencyTtlSeconds) {
        DomainValidationException.positive(id, "HTTP config id");
        DomainValidationException.positive(provenanceId, "Provenance id");
        String retryAfterTrimmed = DomainValidationException.notBlank(retryAfterPolicyCode, "Retry-after policy code");
        DomainValidationException.nonNull(effectiveFrom, "Effective from");

        this.id = id;
        this.provenanceId = provenanceId;
        this.operationType = operationType != null ? operationType.trim() : null;
        this.operationTypeKey = operationTypeKey != null ? operationTypeKey.trim() : "ALL";
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.defaultHeadersJson = defaultHeadersJson;
        this.timeoutConnectMillis = timeoutConnectMillis;
        this.timeoutReadMillis = timeoutReadMillis;
        this.timeoutTotalMillis = timeoutTotalMillis;
        this.tlsVerifyEnabled = tlsVerifyEnabled;
        this.proxyUrlValue = proxyUrlValue != null ? proxyUrlValue.trim() : null;
        this.retryAfterPolicyCode = retryAfterTrimmed;
        this.retryAfterCapMillis = retryAfterCapMillis;
        this.idempotencyHeaderName = idempotencyHeaderName != null ? idempotencyHeaderName.trim() : null;
        this.idempotencyTtlSeconds = idempotencyTtlSeconds;
    }
}
