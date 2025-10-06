package com.patra.registry.domain.model.read.provenance;

import com.patra.registry.domain.exception.DomainValidationException;

import java.time.Instant;

/**
 * Retry configuration query view.
 *
 * <p>Read-optimized projection for querying retry policy configuration.
 *
 * @author linqibin
 * @since 0.1.0
 */
public record RetryConfigQuery(
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
        Integer circuitCooldownMillis
) {
    public RetryConfigQuery {
        if (id == null || id <= 0) {
            throw new DomainValidationException("Retry config id must be positive");
        }
        if (provenanceId == null || provenanceId <= 0) {
            throw new DomainValidationException("Provenance id must be positive");
        }
        if (backoffPolicyTypeCode == null || backoffPolicyTypeCode.isBlank()) {
            throw new DomainValidationException("Backoff policy type code cannot be blank");
        }
        if (effectiveFrom == null) {
            throw new DomainValidationException("Effective from cannot be null");
        }
        operationType = operationType != null ? operationType.trim() : null;
        backoffPolicyTypeCode = backoffPolicyTypeCode.trim();
    }
}
