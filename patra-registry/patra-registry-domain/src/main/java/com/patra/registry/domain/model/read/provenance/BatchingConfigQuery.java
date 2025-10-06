package com.patra.registry.domain.model.read.provenance;

import com.patra.registry.domain.exception.DomainValidationException;

import java.time.Instant;

/**
 * Batching configuration query view.
 *
 * <p>Read-optimized projection for querying batching policy configuration.
 *
 * @author linqibin
 * @since 0.1.0
 */
public record BatchingConfigQuery(
        Long id,
        Long provenanceId,
        String operationType,
        Instant effectiveFrom,
        Instant effectiveTo,
        Integer detailFetchBatchSize,
        String idsParamName,
        String idsJoinDelimiter,
        Integer maxIdsPerRequest
) {
    public BatchingConfigQuery {
        if (id == null || id <= 0) {
            throw new DomainValidationException("Batching config id must be positive");
        }
        if (provenanceId == null || provenanceId <= 0) {
            throw new DomainValidationException("Provenance id must be positive");
        }
        if (effectiveFrom == null) {
            throw new DomainValidationException("Effective from cannot be null");
        }
        operationType = operationType != null ? operationType.trim() : null;
        idsParamName = idsParamName != null ? idsParamName.trim() : null;
        idsJoinDelimiter = idsJoinDelimiter != null ? idsJoinDelimiter.trim() : null;
    }
}
