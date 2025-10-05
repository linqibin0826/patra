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
        /* Primary key; unique batching configuration identifier */
        Long id,
        /* Foreign key referencing {@code reg_provenance.id} */
        Long provenanceId,
        /* Operation type discriminator (HARVEST/UPDATE/BACKFILL); {@code null} applies to all */
        String operationType,
        /* Normalized operation type key; defaults to {@code ALL} when {@code operationType} is {@code null} */
        String operationTypeKey,
        /* Inclusive timestamp marking when this batching configuration becomes effective */
        Instant effectiveFrom,
        /* Exclusive timestamp marking when this batching configuration expires; {@code null} means open-ended */
        Instant effectiveTo,
        /* Batch size for detail fetch operations; number of IDs to process per batch, {@code null} means no batching */
        Integer detailFetchBatchSize,
        /* Query parameter name for IDs list (e.g., ids, pmids); {@code null} means single ID per request */
        String idsParamName,
        /* Delimiter for joining multiple IDs (e.g., comma, pipe); {@code null} means use default comma */
        String idsJoinDelimiter,
        /* Maximum IDs allowed per single request; API-imposed limit, {@code null} means no limit */
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
