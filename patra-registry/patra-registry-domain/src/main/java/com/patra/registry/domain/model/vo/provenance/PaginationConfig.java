package com.patra.registry.domain.model.vo.provenance;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;

/**
 * Domain value object for {@code reg_prov_pagination_cfg}.
 *
 * <p>Defines pagination strategy and key parameters at SOURCE/TASK scope.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record PaginationConfig(
        /* Primary key; unique pagination configuration identifier */
        Long id,
        /* Foreign key referencing {@code reg_provenance.id} */
        Long provenanceId,
        /* Operation type discriminator (HARVEST/UPDATE/BACKFILL); {@code null} applies to all */
        String operationType,
        /* Normalized operation type key; defaults to {@code ALL} when {@code operationType} is {@code null} */
        String operationTypeKey,
        /* Inclusive timestamp marking when this pagination configuration becomes effective */
        Instant effectiveFrom,
        /* Exclusive timestamp marking when this pagination configuration expires; {@code null} means open-ended */
        Instant effectiveTo,
        /* Pagination mode code (DICT CODE: pagination_mode); defines strategy (OFFSET/CURSOR/PAGE_NUMBER/LINK) */
        String paginationModeCode,
        /* Number of records per page/batch; must be positive */
        Integer pageSizeValue,
        /* Maximum pages to fetch per single execution; {@code null} means no limit (use cautiously) */
        Integer maxPagesPerExecution,
        /* Query parameter name for sort field (e.g., sort_by, order); {@code null} means no sorting */
        String sortFieldParamName,
        /* Sort direction indicator (1=ASC, -1=DESC, 0=unspecified); {@code null} means source default */
        Integer sortingDirection
) {
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
