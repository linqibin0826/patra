package com.patra.registry.api.dto.provenance;

import java.time.Instant;

/**
 * Response DTO describing retry and backoff configuration for provenance calls.
 *
 * <p>Field descriptions:
 *
 * <ol>
 *   <li>id - primary identifier of the retry configuration row
 *   <li>provenanceId - provenance owning the configuration
 *   <li>operationType - operation discriminator the configuration applies to
 *   <li>effectiveFrom - timestamp from which the configuration becomes effective
 *   <li>effectiveTo - timestamp until which the configuration remains effective
 *   <li>maxRetryTimes - maximum retries excluding the first attempt
 *   <li>backoffPolicyTypeCode - backoff strategy (FIXED/EXPONENTIAL/EXP_JITTER/LINEAR)
 *   <li>initialDelayMillis - initial delay in milliseconds before first retry
 *   <li>maxDelayMillis - upper bound for individual retry delay in milliseconds
 *   <li>expMultiplierValue - multiplier applied for exponential backoff
 *   <li>jitterFactorRatio - jitter ratio applied for randomization (0-1)
 *   <li>retryHttpStatusJson - serialized HTTP statuses that trigger retry
 *   <li>giveupHttpStatusJson - serialized HTTP statuses that stop retrying
 *   <li>retryOnNetworkError - whether network exceptions trigger retry
 *   <li>circuitBreakThreshold - failure threshold activating circuit breaker
 *   <li>circuitCooldownMillis - cooldown duration in milliseconds after breaker trips
 * </ol>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record RetryConfigResp(
    Long id,
    Long provenanceId,
    String operationType,
    Instant effectiveFrom,
    Instant effectiveTo,
    Integer maxRetryTimes,
    String backoffPolicyTypeCode,
    Integer initialDelayMillis,
    Integer maxDelayMillis,
    Double expMultiplierValue,
    Double jitterFactorRatio,
    String retryHttpStatusJson,
    String giveupHttpStatusJson,
    boolean retryOnNetworkError,
    Integer circuitBreakThreshold,
    Integer circuitCooldownMillis) {}
