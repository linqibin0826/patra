package com.patra.registry.domain.model.vo.provenance;

import com.patra.registry.domain.exception.DomainValidationException;
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
        DomainValidationException.positive(id, "Rate limit config id");
        DomainValidationException.positive(provenanceId, "Provenance id");
        String scopeTrimmed = DomainValidationException.notBlank(scopeCode, "Scope code");
        String bucketScopeTrimmed = DomainValidationException.notBlank(bucketGranularityScopeCode, "Bucket granularity scope code");
        DomainValidationException.nonNull(effectiveFrom, "Effective from");

        this.id = id; // 已验证
        this.provenanceId = provenanceId; // 已验证
        this.scopeCode = scopeTrimmed;
        this.taskType = taskType != null ? taskType.trim() : null;
        this.taskTypeKey = taskTypeKey != null ? taskTypeKey.trim() : "ALL";
        this.effectiveFrom = effectiveFrom; // 非 null 已验证
        this.effectiveTo = effectiveTo;
        this.rateTokensPerSecond = rateTokensPerSecond;
        this.burstBucketCapacity = burstBucketCapacity;
        this.maxConcurrentRequests = maxConcurrentRequests;
        this.perCredentialQpsLimit = perCredentialQpsLimit;
        this.bucketGranularityScopeCode = bucketScopeTrimmed;
        this.smoothingWindowMillis = smoothingWindowMillis;
        this.respectServerRateHeader = respectServerRateHeader;
        this.endpointId = endpointId;
        this.credentialName = credentialName != null ? credentialName.trim() : null;
    }
}
