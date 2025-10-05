package com.patra.registry.domain.model.vo.provenance;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;

/**
 * Domain value object for {@code reg_prov_http_cfg}.
 *
 * <p>Represents HTTP policy overrides (headers/timeouts/TLS/proxy/idempotency)
 * at SOURCE/TASK scope for a provenance.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record HttpConfig(
        /* Primary key; unique HTTP configuration identifier */
        Long id,
        /* Foreign key referencing {@code reg_provenance.id} */
        Long provenanceId,
        /* Operation type discriminator (HARVEST/UPDATE/BACKFILL); {@code null} applies to all */
        String operationType,
        /* Normalized operation type key; defaults to {@code ALL} when {@code operationType} is {@code null} */
        String operationTypeKey,
        /* Inclusive timestamp marking when this HTTP configuration becomes effective */
        Instant effectiveFrom,
        /* Exclusive timestamp marking when this HTTP configuration expires; {@code null} means open-ended */
        Instant effectiveTo,
        /* JSON object containing default HTTP headers (e.g., {"User-Agent":"...", "Accept":"..."}) */
        String defaultHeadersJson,
        /* Connection timeout in milliseconds; {@code null} means use client default */
        Integer timeoutConnectMillis,
        /* Read timeout in milliseconds; {@code null} means use client default */
        Integer timeoutReadMillis,
        /* Total timeout in milliseconds for entire request/response cycle; {@code null} means no limit */
        Integer timeoutTotalMillis,
        /* Whether TLS certificate verification is enabled; {@code true}=verify, {@code false}=skip (use cautiously) */
        boolean tlsVerifyEnabled,
        /* Proxy URL (e.g., http://proxy.example.com:8080); {@code null} means direct connection */
        String proxyUrlValue,
        /* Retry-After header handling policy code (DICT CODE: retry_after_policy); defines how to parse/apply server backoff */
        String retryAfterPolicyCode,
        /* Maximum Retry-After delay cap in milliseconds; prevents excessively long server-suggested delays */
        Integer retryAfterCapMillis,
        /* HTTP header name for idempotency key (e.g., Idempotency-Key); {@code null} means no idempotency header */
        String idempotencyHeaderName,
        /* TTL in seconds for idempotency key caching/uniqueness window; {@code null} means use default TTL */
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
