package com.patra.registry.domain.model.read.provenance;

import com.patra.registry.domain.exception.DomainValidationException;

import java.time.Instant;

/**
 * 重试与退避配置查询视图。
 */
public record RetryConfigQuery(
        Long id,
        Long provenanceId,
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
        taskType = taskType != null ? taskType.trim() : null;
        taskTypeKey = taskTypeKey != null ? taskTypeKey.trim() : "ALL";
        backoffPolicyTypeCode = backoffPolicyTypeCode.trim();
    }
}
