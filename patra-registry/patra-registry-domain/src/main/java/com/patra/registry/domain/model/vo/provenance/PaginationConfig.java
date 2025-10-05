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
        Long id,
        Long provenanceId,
        String operationType,
        String operationTypeKey,
        Instant effectiveFrom,
        Instant effectiveTo,
        String paginationModeCode,
        Integer pageSizeValue,
        Integer maxPagesPerExecution,
        String sortFieldParamName,
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
