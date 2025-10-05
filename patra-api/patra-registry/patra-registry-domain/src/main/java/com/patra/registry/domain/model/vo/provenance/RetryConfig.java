package com.patra.registry.domain.model.vo.provenance;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;

/**
 * Domain value object for {@code reg_prov_retry_cfg}.
 *
 * <p>Contains retry/backoff/jitter/circuit breaker policy at SOURCE/TASK scope.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record RetryConfig(
        /* Primary key; unique retry configuration identifier */
        Long id,
        /* Foreign key referencing {@code reg_provenance.id} */
        Long provenanceId,
        /* Operation type discriminator (HARVEST/UPDATE/BACKFILL); {@code null} applies to all */
        String operationType,
        /* Normalized operation type key; defaults to {@code ALL} when {@code operationType} is {@code null} */
        String operationTypeKey,
        /* Inclusive timestamp marking when this retry configuration becomes effective */
        Instant effectiveFrom,
        /* Exclusive timestamp marking when this retry configuration expires; {@code null} means open-ended */
        Instant effectiveTo,
        /* Maximum number of retry attempts; {@code 0} means no retry, {@code null} means use default */
        Integer maxRetryTimes,
        /* Backoff policy type code (DICT CODE: backoff_policy_type); defines strategy (FIXED/LINEAR/EXPONENTIAL) */
        String backoffPolicyTypeCode,
        /* Initial delay before first retry in milliseconds; must be non-negative */
        Integer initialDelayMillis,
        /* Maximum delay cap in milliseconds; prevents unbounded backoff growth */
        Integer maxDelayMillis,
        /* Exponential backoff multiplier (e.g., 2.0 for doubling); only applies to EXPONENTIAL policy */
        Double expMultiplierValue,
        /* Jitter factor ratio (0.0-1.0); adds randomness to prevent thundering herd */
        Double jitterFactorRatio,
        /* JSON array of HTTP status codes triggering retry (e.g., [429, 503]); {@code null} means use defaults */
        String retryHttpStatusJson,
        /* JSON array of HTTP status codes causing immediate give-up (e.g., [400, 401, 403]); {@code null} means none */
        String giveupHttpStatusJson,
        /* Whether to retry on network-level errors (connection timeout/reset); {@code true}=retry, {@code false}=fail fast */
        boolean retryOnNetworkError,
        /* Circuit breaker failure threshold; opens circuit after N consecutive failures, {@code null} means disabled */
        Integer circuitBreakThreshold,
        /* Circuit breaker cooldown period in milliseconds before half-open attempt; {@code null} means use default */
        Integer circuitCooldownMillis
) {
    public RetryConfig(Long id,
                       Long provenanceId,
                       String operationType,
                       String operationTypeKey,
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
                       Integer circuitCooldownMillis) {
        DomainValidationException.positive(id, "Retry config id");
        DomainValidationException.positive(provenanceId, "Provenance id");
        String backoffTrimmed = DomainValidationException.notBlank(backoffPolicyTypeCode, "Backoff policy type code");
        DomainValidationException.nonNull(effectiveFrom, "Effective from");

        this.id = id;
        this.provenanceId = provenanceId;
        this.operationType = operationType != null ? operationType.trim() : null;
        this.operationTypeKey = operationTypeKey != null ? operationTypeKey.trim() : "ALL";
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.maxRetryTimes = maxRetryTimes;
        this.backoffPolicyTypeCode = backoffTrimmed;
        this.initialDelayMillis = initialDelayMillis;
        this.maxDelayMillis = maxDelayMillis;
        this.expMultiplierValue = expMultiplierValue;
        this.jitterFactorRatio = jitterFactorRatio;
        this.retryHttpStatusJson = retryHttpStatusJson;
        this.giveupHttpStatusJson = giveupHttpStatusJson;
        this.retryOnNetworkError = retryOnNetworkError;
        this.circuitBreakThreshold = circuitBreakThreshold;
        this.circuitCooldownMillis = circuitCooldownMillis;
    }
}
