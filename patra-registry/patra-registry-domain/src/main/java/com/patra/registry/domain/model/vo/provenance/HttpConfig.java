package com.patra.registry.domain.model.vo.provenance;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;

/**
 * Domain value object for {@code reg_prov_http_cfg}.
 *
 * <p>Configure base_url override, default headers, timeouts, TLS, proxy, Retry-After handling,
 * idempotency, etc. Combined with endpoint/pagination/batching/retry/rate-limit to form
 * the execution contract.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record HttpConfig(
        /* Primary key; unique HTTP configuration identifier */
        Long id,
        /* Foreign key referencing reg_provenance.id */
        Long provenanceId,
        /* Operation type discriminator (ALL/HARVEST/UPDATE/BACKFILL); null applies to all */
        String operationType,
        /* Normalized operation type key; defaults to ALL when operationType is null */
        String operationTypeKey,
        /* Inclusive timestamp marking when this HTTP configuration becomes effective */
        Instant effectiveFrom,
        /* Exclusive timestamp marking when this HTTP configuration expires; null means open-ended */
        Instant effectiveTo,
        /* Default HTTP headers (JSON); merged with runtime request headers */
        String defaultHeadersJson,
        /* Connect timeout (ms): establishing TCP/SSL */
        Integer timeoutConnectMillis,
        /* Read timeout (ms): reading response body */
        Integer timeoutReadMillis,
        /* Total timeout (ms): request end-to-end cap */
        Integer timeoutTotalMillis,
        /* Verify TLS certificates: true=on, false=off (test only) */
        boolean tlsVerifyEnabled,
        /* Proxy URL: e.g., http://user:pass@host:port or socks5://host:port */
        String proxyUrlValue,
        /* Retry-After policy code (DICT CODE: retry_after_policy): IGNORE/RESPECT/CLAMP */
        String retryAfterPolicyCode,
        /* Max wait cap (ms) when RESPECT/CLAMP is used */
        Integer retryAfterCapMillis,
        /* Idempotency header name (e.g., Idempotency-Key) to avoid duplicate submissions */
        String idempotencyHeaderName,
        /* Idempotency key TTL (seconds); effective only when supported */
        Integer idempotencyTtlSeconds
) {
    /**
     * Canonical constructor with validation.
     *
     * @param id unique configuration identifier, must be positive
     * @param provenanceId provenance identifier, must be positive
     * @param operationType operation type discriminator, nullable
     * @param operationTypeKey normalized operation type key, defaults to "ALL"
     * @param effectiveFrom effective start timestamp, must not be null
     * @param effectiveTo effective end timestamp, nullable (open-ended)
     * @param defaultHeadersJson default headers as JSON string, nullable
     * @param timeoutConnectMillis connection timeout in milliseconds, nullable
     * @param timeoutReadMillis read timeout in milliseconds, nullable
     * @param timeoutTotalMillis total timeout in milliseconds, nullable
     * @param tlsVerifyEnabled whether to verify TLS certificates
     * @param proxyUrlValue proxy URL, nullable
     * @param retryAfterPolicyCode retry-after policy code from dictionary, must not be blank
     * @param retryAfterCapMillis retry-after cap in milliseconds, nullable
     * @param idempotencyHeaderName idempotency header name, nullable
     * @param idempotencyTtlSeconds idempotency TTL in seconds, nullable
     * @throws DomainValidationException if validation fails
     */
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
