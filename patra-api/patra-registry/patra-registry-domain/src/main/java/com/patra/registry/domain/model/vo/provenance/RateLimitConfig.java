package com.patra.registry.domain.model.vo.provenance;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;

/**
 * Domain value object for {@code reg_prov_rate_limit_cfg}.
 *
 * <p>Captures concurrency limits and per-credential QPS caps at SOURCE/TASK scope.</p>
 *
 * @author linqibin
 * @since 0.1.0
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
