package com.patra.registry.domain.model.vo.provenance;

import java.time.Instant;

/**
 * {@code reg_prov_rate_limit_cfg} 的领域值对象。
 */
public record RateLimitConfig(
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
    public RateLimitConfig(Long id,
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
                           String credentialName) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Rate limit config id must be positive");
        }
        if (provenanceId == null || provenanceId <= 0) {
            throw new IllegalArgumentException("Provenance id must be positive");
        }
        if (scopeCode == null || scopeCode.isBlank()) {
            throw new IllegalArgumentException("Scope code cannot be blank");
        }
        if (bucketGranularityScopeCode == null || bucketGranularityScopeCode.isBlank()) {
            throw new IllegalArgumentException("Bucket granularity scope code cannot be blank");
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
        this.rateTokensPerSecond = rateTokensPerSecond;
        this.burstBucketCapacity = burstBucketCapacity;
        this.maxConcurrentRequests = maxConcurrentRequests;
        this.perCredentialQpsLimit = perCredentialQpsLimit;
        this.bucketGranularityScopeCode = bucketGranularityScopeCode.trim();
        this.smoothingWindowMillis = smoothingWindowMillis;
        this.respectServerRateHeader = respectServerRateHeader;
        this.endpointId = endpointId;
        this.credentialName = credentialName != null ? credentialName.trim() : null;
    }
}
