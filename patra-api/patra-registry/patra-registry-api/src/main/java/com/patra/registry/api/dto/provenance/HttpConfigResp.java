package com.patra.registry.api.dto.provenance;

import java.time.Instant;

/**
 * Response DTO describing baseline HTTP configuration for a provenance.
 *
 * <p>Field descriptions:
 *
 * <ol>
 *   <li>id - primary identifier of the HTTP configuration row
 *   <li>provenanceId - provenance owning the configuration
 *   <li>operationType - operation discriminator (e.g., HARVEST/UPDATE)
 *   <li>effectiveFrom - timestamp from which the configuration becomes effective
 *   <li>effectiveTo - timestamp until which the configuration remains effective
 *   <li>defaultHeadersJson - serialized default headers applied to outbound calls
 *   <li>timeoutConnectMillis - connection timeout in milliseconds
 *   <li>timeoutReadMillis - read timeout in milliseconds
 *   <li>timeoutTotalMillis - overall request timeout in milliseconds
 *   <li>tlsVerifyEnabled - whether TLS certificate verification is enabled
 *   <li>proxyUrlValue - optional proxy URL used for outbound traffic
 *   <li>retryAfterPolicyCode - policy applied when handling Retry-After headers
 *   <li>retryAfterCapMillis - maximum wait time in milliseconds when honoring Retry-After
 *   <li>idempotencyHeaderName - header name injected for idempotency tracking
 *   <li>idempotencyTtlSeconds - expected TTL for idempotency tokens on provider side
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
    Integer idempotencyTtlSeconds) {}
