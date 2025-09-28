package com.patra.registry.domain.model.read.provenance;

import com.patra.registry.domain.exception.DomainValidationException;

import java.time.Instant;

/**
 * 限流与并发配置查询视图。
 */
public record RateLimitConfigQuery(
        Long id,
        Long provenanceId,
        String scopeCode,
        String taskType,
        String taskTypeKey,
        Instant effectiveFrom,
        Instant effectiveTo,
        Integer rateTokensPerSecond,
        Integer burstBucketCapacity,
        Integer maxConcurrentRequests,
        Integer perCredentialQpsLimit,
        String bucketGranularityScopeCode,
        Integer smoothingWindowMillis,
        boolean respectServerRateHeader,
        Long endpointId,
        String credentialName
) {
    public RateLimitConfigQuery {
        if (id == null || id <= 0) {
            throw new DomainValidationException("Rate limit config id must be positive");
        }
        if (provenanceId == null || provenanceId <= 0) {
            throw new DomainValidationException("Provenance id must be positive");
        }
        if (scopeCode == null || scopeCode.isBlank()) {
            throw new DomainValidationException("Scope code cannot be blank");
        }
        if (bucketGranularityScopeCode == null || bucketGranularityScopeCode.isBlank()) {
            throw new DomainValidationException("Bucket granularity scope code cannot be blank");
        }
        if (effectiveFrom == null) {
            throw new DomainValidationException("Effective from cannot be null");
        }
        scopeCode = scopeCode.trim();
        taskType = taskType != null ? taskType.trim() : null;
        taskTypeKey = taskTypeKey != null ? taskTypeKey.trim() : "ALL";
        bucketGranularityScopeCode = bucketGranularityScopeCode.trim();
        credentialName = credentialName != null ? credentialName.trim() : null;
    }
}
