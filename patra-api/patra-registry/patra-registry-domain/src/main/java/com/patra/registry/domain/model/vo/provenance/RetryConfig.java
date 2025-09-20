package com.patra.registry.domain.model.vo.provenance;

import java.time.Instant;

/**
 * {@code reg_prov_retry_cfg} 的领域值对象。
 */
public record RetryConfig(
        Long id,
        Long provenanceId,
        String scopeCode,
        String taskType,
        String taskTypeKey,
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
                       String scopeCode,
                       String taskType,
                       String taskTypeKey,
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
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Retry config id must be positive");
        }
        if (provenanceId == null || provenanceId <= 0) {
            throw new IllegalArgumentException("Provenance id must be positive");
        }
        if (scopeCode == null || scopeCode.isBlank()) {
            throw new IllegalArgumentException("Scope code cannot be blank");
        }
        if (backoffPolicyTypeCode == null || backoffPolicyTypeCode.isBlank()) {
            throw new IllegalArgumentException("Backoff policy type code cannot be blank");
        }
        if (effectiveFrom == null) {
            throw new IllegalArgumentException("Effective from cannot be null");
        }

        this.id = id;
        this.provenanceId = provenanceId;
        this.scopeCode = scopeCode.trim();
        this.taskType = taskType != null ? taskType.trim() : null;
        this.taskTypeKey = taskTypeKey != null ? taskTypeKey.trim() : "ALL";
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.maxRetryTimes = maxRetryTimes;
        this.backoffPolicyTypeCode = backoffPolicyTypeCode.trim();
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
