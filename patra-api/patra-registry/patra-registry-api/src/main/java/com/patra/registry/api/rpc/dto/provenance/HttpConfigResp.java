package com.patra.registry.api.rpc.dto.provenance;

import java.time.Instant;

/**
 * Response DTO describing baseline HTTP configuration for a provenance.
 *
 * <p>Field descriptions:
 * <ol>
 *   <li>id - primary identifier of the HTTP configuration row</li>
 *   <li>provenanceId - provenance owning the configuration</li>
 *   <li>operationType - operation discriminator (e.g., HARVEST/UPDATE)</li>
 *   <li>effectiveFrom - timestamp from which the configuration becomes effective</li>
 *   <li>effectiveTo - timestamp until which the configuration remains effective</li>
 *   <li>defaultHeadersJson - serialized default headers applied to outbound calls</li>
 *   <li>timeoutConnectMillis - connection timeout in milliseconds</li>
 *   <li>timeoutReadMillis - read timeout in milliseconds</li>
 *   <li>timeoutTotalMillis - overall request timeout in milliseconds</li>
 *   <li>tlsVerifyEnabled - whether TLS certificate verification is enabled</li>
 *   <li>proxyUrlValue - optional proxy URL used for outbound traffic</li>
 *   <li>retryAfterPolicyCode - policy applied when handling Retry-After headers</li>
 *   <li>retryAfterCapMillis - maximum wait time in milliseconds when honoring Retry-After</li>
 *   <li>idempotencyHeaderName - header name injected for idempotency tracking</li>
 *   <li>idempotencyTtlSeconds - expected TTL for idempotency tokens on provider side</li>
 * </ol>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record HttpConfigResp(
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
}
