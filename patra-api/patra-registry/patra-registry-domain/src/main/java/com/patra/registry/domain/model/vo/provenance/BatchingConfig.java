package com.patra.registry.domain.model.vo.provenance;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;

/**
 * Domain value object for {@code reg_prov_batching_cfg}.
 *
 * <p>Controls batched detail request shaping and related parameters.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record BatchingConfig(
        Long id,
        Long provenanceId,
        String operationType,
        String operationTypeKey,
        Instant effectiveFrom,
        Instant effectiveTo,
        Integer detailFetchBatchSize,
        String idsParamName,
        String idsJoinDelimiter,
        Integer maxIdsPerRequest
) {
    public BatchingConfig(Long id,
                          Long provenanceId,
                          String operationType,
                          String operationTypeKey,
                          Instant effectiveFrom,
                          Instant effectiveTo,
                          Integer detailFetchBatchSize,
                          String idsParamName,
                          String idsJoinDelimiter,
                          Integer maxIdsPerRequest) {
        DomainValidationException.positive(id, "Batching config id");
        DomainValidationException.positive(provenanceId, "Provenance id");
        DomainValidationException.nonNull(effectiveFrom, "Effective from");

        this.id = id;
        this.provenanceId = provenanceId;
        this.operationType = operationType != null ? operationType.trim() : null;
        this.operationTypeKey = operationTypeKey != null ? operationTypeKey.trim() : "ALL";
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.detailFetchBatchSize = detailFetchBatchSize;
        this.idsParamName = idsParamName != null ? idsParamName.trim() : null;
        this.idsJoinDelimiter = idsJoinDelimiter != null ? idsJoinDelimiter.trim() : null;
        this.maxIdsPerRequest = maxIdsPerRequest;
    }
}
