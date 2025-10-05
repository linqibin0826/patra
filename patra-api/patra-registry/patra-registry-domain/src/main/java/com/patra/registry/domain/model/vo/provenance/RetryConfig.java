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
        Long id,
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
