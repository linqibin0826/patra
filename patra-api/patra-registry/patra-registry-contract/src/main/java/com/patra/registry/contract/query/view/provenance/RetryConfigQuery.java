package com.patra.registry.contract.query.view.provenance;

import java.time.Instant;

/**
 * 重试与退避配置查询视图。
 */
public record RetryConfigQuery(
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
    public RetryConfigQuery {
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
        scopeCode = scopeCode.trim();
        taskType = taskType != null ? taskType.trim() : null;
        taskTypeKey = taskTypeKey != null ? taskTypeKey.trim() : "ALL";
        backoffPolicyTypeCode = backoffPolicyTypeCode.trim();
    }
}
