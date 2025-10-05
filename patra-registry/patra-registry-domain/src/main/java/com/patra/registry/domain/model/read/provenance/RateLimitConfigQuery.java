package com.patra.registry.domain.model.read.provenance;

import com.patra.registry.domain.exception.DomainValidationException;

import java.time.Instant;

/**
 * 限流与并发配置查询视图。
 */
public record RateLimitConfigQuery(
        Long id,
        Long provenanceId,
        String operationType,
        Instant effectiveFrom,
        Instant effectiveTo,
        Integer maxConcurrentRequests,
        Integer perCredentialQpsLimit
) {
    public RateLimitConfigQuery {
        if (id == null || id <= 0) {
            throw new DomainValidationException("Rate limit config id must be positive");
        }
        if (provenanceId == null || provenanceId <= 0) {
            throw new DomainValidationException("Provenance id must be positive");
        }
        if (effectiveFrom == null) {
            throw new DomainValidationException("Effective from cannot be null");
        }
        operationType = operationType != null ? operationType.trim() : null;
    }
}
