package com.patra.registry.api.rpc.dto.provenance;

import java.time.Instant;

/**
 * 限流配置响应 DTO。
 */
public record RateLimitConfigResp(
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
}
