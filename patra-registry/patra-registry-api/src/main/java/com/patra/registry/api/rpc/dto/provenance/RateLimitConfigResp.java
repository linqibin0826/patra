package com.patra.registry.api.rpc.dto.provenance;

import java.time.Instant;

/**
 * Response DTO describing rate limiting and concurrency configuration.
 *
 * <p>Field descriptions:
 *
 * <ol>
 *   <li>id - primary identifier of the rate limit configuration row
 *   <li>provenanceId - provenance owning the configuration
 *   <li>operationType - operation discriminator the configuration applies to
 *   <li>effectiveFrom - timestamp from which the configuration becomes effective
 *   <li>effectiveTo - timestamp until which the configuration remains effective
 *   <li>maxConcurrentRequests - maximum concurrent HTTP requests allowed
 *   <li>perCredentialQpsLimit - QPS limit enforced per credential
 * </ol>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record RateLimitConfigResp(
    Long id,
    Long provenanceId,
    String operationType,
    Instant effectiveFrom,
    Instant effectiveTo,
    Integer maxConcurrentRequests,
    Integer perCredentialQpsLimit) {}
