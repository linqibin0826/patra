package com.patra.registry.domain.model.vo.provenance;

import com.patra.registry.domain.exception.DomainValidationException;

import java.time.Instant;

/**
 * Domain value object for {@code reg_prov_rate_limit_cfg}.
 *
 * <p>Configure QPS/token-bucket, burst capacity, max concurrency, by key/endpoint/IP/task granularity,
 * smoothing/adaptive, etc. Combined with retry and HTTP; may respect server rate headers (Retry-After, RateLimit-*) for smoothing.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record RateLimitConfig(
        /* Primary key; unique rate limit configuration identifier */
        Long id,
        /* Foreign key referencing reg_provenance.id */
        Long provenanceId,
        /* Operation type discriminator (HARVEST/UPDATE/BACKFILL); null applies to all */
        String operationType,
        /* Inclusive timestamp marking when this rate limit configuration becomes effective */
        Instant effectiveFrom,
        /* Exclusive timestamp marking when this rate limit configuration expires; null means open-ended */
        Instant effectiveTo,
        /* Maximum concurrent requests allowed globally; null means no concurrency limit */
        Integer maxConcurrentRequests,
        /* QPS (Queries Per Second) limit per credential/API key; null means no per-credential limit */
        Integer perCredentialQpsLimit
) {
    /**
     * Canonical constructor with validation.
     *
     * @param id                    unique configuration identifier, must be positive
     * @param provenanceId          provenance identifier, must be positive
     * @param operationType         operation type discriminator, nullable
     * @param effectiveFrom         effective start timestamp, must not be null
     * @param effectiveTo           effective end timestamp, nullable (open-ended)
     * @param maxConcurrentRequests maximum concurrent requests, nullable
     * @param perCredentialQpsLimit per-credential QPS limit, nullable
     * @throws DomainValidationException if validation fails
     */
    public RateLimitConfig(Long id,
                           Long provenanceId,
                           String operationType,
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
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.maxConcurrentRequests = maxConcurrentRequests;
        this.perCredentialQpsLimit = perCredentialQpsLimit;
    }
}
