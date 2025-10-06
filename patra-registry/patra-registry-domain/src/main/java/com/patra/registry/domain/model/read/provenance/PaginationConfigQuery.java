package com.patra.registry.domain.model.read.provenance;

import com.patra.registry.domain.exception.DomainValidationException;

import java.time.Instant;

/**
 * Pagination configuration query view.
 *
 * <p>Read-optimized projection for querying pagination strategy configuration.
 *
 * @author linqibin
 * @since 0.1.0
 */
public record PaginationConfigQuery(
        Long id,
        Long provenanceId,
        String operationType,
        Instant effectiveFrom,
        Instant effectiveTo,
        String paginationModeCode,
        Integer pageSizeValue,
        Integer maxPagesPerExecution,
        String sortFieldParamName,
        Integer sortingDirection
) {
    public PaginationConfigQuery {
        if (id == null || id <= 0) {
            throw new DomainValidationException("Pagination config id must be positive");
        }
        if (provenanceId == null || provenanceId <= 0) {
            throw new DomainValidationException("Provenance id must be positive");
        }
        if (paginationModeCode == null || paginationModeCode.isBlank()) {
            throw new DomainValidationException("Pagination mode code cannot be blank");
        }
        if (effectiveFrom == null) {
            throw new DomainValidationException("Effective from cannot be null");
        }
        operationType = operationType != null ? operationType.trim() : null;
        paginationModeCode = paginationModeCode.trim();
        sortFieldParamName = sortFieldParamName != null ? sortFieldParamName.trim() : null;
    }
}
