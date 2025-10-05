package com.patra.registry.domain.model.vo.provenance;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;

/**
 * Domain value object for {@code reg_prov_pagination_cfg}.
 *
 * <p>Configure page/cursor/token/scroll pagination params and response extraction rules (JSONPath/XPath).
 * At most one currently effective row per endpoint definition; endpoint-level overrides take precedence.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record PaginationConfig(
        /* Primary key; unique pagination configuration identifier */
        Long id,
        /* Foreign key referencing reg_provenance.id */
        Long provenanceId,
        /* Operation type discriminator (HARVEST/UPDATE/BACKFILL); null applies to all */
        String operationType,
        /* Normalized operation type key; defaults to ALL when operationType is null */
        String operationTypeKey,
        /* Inclusive timestamp marking when this pagination configuration becomes effective */
        Instant effectiveFrom,
        /* Exclusive timestamp marking when this pagination configuration expires; null means open-ended */
        Instant effectiveTo,
        /* Pagination mode code (DICT CODE: pagination_mode); PAGE_NUMBER/CURSOR/TOKEN/SCROLL */
        String paginationModeCode,
        /* Page size for PAGE_NUMBER/SCROLL; null uses application default */
        Integer pageSizeValue,
        /* Maximum pages per single execution to cap deep pagination */
        Integer maxPagesPerExecution,
        /* Sort field parameter name */
        String sortFieldParamName,
        /* Sort order: 0=DESC, 1=ASC */
        Integer sortingDirection
) {
    /**
     * Canonical constructor with validation.
     *
     * @param id unique configuration identifier, must be positive
     * @param provenanceId provenance identifier, must be positive
     * @param operationType operation type discriminator, nullable
     * @param operationTypeKey normalized operation type key, defaults to "ALL"
     * @param effectiveFrom effective start timestamp, must not be null
     * @param effectiveTo effective end timestamp, nullable (open-ended)
     * @param paginationModeCode pagination mode code from dictionary, must not be blank
     * @param pageSizeValue page size value, nullable
     * @param maxPagesPerExecution maximum pages per execution, nullable
     * @param sortFieldParamName sort field parameter name, nullable
     * @param sortingDirection sort direction (0=DESC, 1=ASC), nullable
     * @throws DomainValidationException if validation fails
     */
    public PaginationConfig(Long id,
                            Long provenanceId,
                            String operationType,
                            String operationTypeKey,
                            Instant effectiveFrom,
                            Instant effectiveTo,
                            String paginationModeCode,
                            Integer pageSizeValue,
                            Integer maxPagesPerExecution,
                            String sortFieldParamName,
                            Integer sortingDirection) {
        DomainValidationException.positive(id, "Pagination config id");
        DomainValidationException.positive(provenanceId, "Provenance id");
        String modeTrimmed = DomainValidationException.notBlank(paginationModeCode, "Pagination mode code");
        DomainValidationException.nonNull(effectiveFrom, "Effective from");

        this.id = id;
        this.provenanceId = provenanceId;
        this.operationType = operationType != null ? operationType.trim() : null;
        this.operationTypeKey = operationTypeKey != null ? operationTypeKey.trim() : "ALL";
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.paginationModeCode = modeTrimmed;
        this.pageSizeValue = pageSizeValue;
        this.maxPagesPerExecution = maxPagesPerExecution;
        this.sortFieldParamName = sortFieldParamName != null ? sortFieldParamName.trim() : null;
        this.sortingDirection = sortingDirection;
    }
}
