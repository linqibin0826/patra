package com.patra.registry.domain.model.vo.provenance;

import com.patra.registry.domain.exception.DomainValidationException;
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
        DomainValidationException.positive(id, "Retry config id");
        DomainValidationException.positive(provenanceId, "Provenance id");
        String scopeTrimmed = DomainValidationException.notBlank(scopeCode, "Scope code");
        String backoffTrimmed = DomainValidationException.notBlank(backoffPolicyTypeCode, "Backoff policy type code");
        DomainValidationException.nonNull(effectiveFrom, "Effective from");

        this.id = id; // 已验证
        this.provenanceId = provenanceId; // 已验证
        this.scopeCode = scopeTrimmed;
        this.taskType = taskType != null ? taskType.trim() : null;
        this.taskTypeKey = taskTypeKey != null ? taskTypeKey.trim() : "ALL";
        this.effectiveFrom = effectiveFrom; // 非 null 已验证
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
