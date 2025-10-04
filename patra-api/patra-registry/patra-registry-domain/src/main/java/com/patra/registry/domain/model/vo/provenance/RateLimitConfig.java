package com.patra.registry.domain.model.vo.provenance;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;

/**
 * {@code reg_prov_rate_limit_cfg} 的领域值对象。
 */
public record RateLimitConfig(
        Long id,
        Long provenanceId,
        String operationType,
        String operationTypeKey,
        Instant effectiveFrom,
        Instant effectiveTo,
        Integer maxConcurrentRequests,
        Integer perCredentialQpsLimit
) {
    public RateLimitConfig(Long id,
                           Long provenanceId,
                           String operationType,
                           String operationTypeKey,
                           Instant effectiveFrom,
                           Instant effectiveTo,
                           Integer maxConcurrentRequests,
                           Integer perCredentialQpsLimit) {
        DomainValidationException.positive(id, "Rate limit config id");
        DomainValidationException.positive(provenanceId, "Provenance id");
        DomainValidationException.nonNull(effectiveFrom, "Effective from");

        this.id = id;
        this.provenanceId = provenanceId;
        this.operationType = operationType != null ? operationType.trim() : null;
        this.operationTypeKey = operationTypeKey != null ? operationTypeKey.trim() : "ALL";
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.maxConcurrentRequests = maxConcurrentRequests;
        this.perCredentialQpsLimit = perCredentialQpsLimit;
    }
}
