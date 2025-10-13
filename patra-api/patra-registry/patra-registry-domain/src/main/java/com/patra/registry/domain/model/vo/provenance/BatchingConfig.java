package com.patra.registry.domain.model.vo.provenance;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;

/**
 * Domain value object for {@code reg_prov_batching_cfg}.
 *
 * <p>Define how to shape batched detail requests (ids parameter name, max batch size, concurrency,
 * compression, backpressure, etc.). Combined with endpoint definition to generate batched detail
 * requests.
 *
 * @author linqibin
 * @since 0.1.0
 */
public record BatchingConfig(
    /* Primary key; unique batching configuration identifier */
    Long id,
    /* Foreign key referencing reg_provenance.id */
    Long provenanceId,
    /* Operation type discriminator (ALL/HARVEST/UPDATE/BACKFILL); null applies to all */
    String operationType,
    /* Inclusive timestamp marking when this batching configuration becomes effective */
    Instant effectiveFrom,
    /* Exclusive timestamp marking when this batching configuration expires; null means open-ended */
    Instant effectiveTo,
    /* Batch size per detail fetch (rows); null uses application default */
    Integer detailFetchBatchSize,
    /* Parameter name for ID list in batch detail requests; null decided by endpoint/app */
    String idsParamName,
    /* Delimiter to join ID list (e.g., comma or plus) */
    String idsJoinDelimiter,
    /* Hard cap of IDs per HTTP request */
    Integer maxIdsPerRequest) {
  /**
   * Canonical constructor with validation.
   *
   * @param id unique configuration identifier, must be positive
   * @param provenanceId provenance identifier, must be positive
   * @param operationType operation type discriminator, nullable
   * @param effectiveFrom effective start timestamp, must not be null
   * @param effectiveTo effective end timestamp, nullable (open-ended)
   * @param detailFetchBatchSize detail fetch batch size, nullable
   * @param idsParamName IDs parameter name, nullable
   * @param idsJoinDelimiter IDs join delimiter, nullable
   * @param maxIdsPerRequest maximum IDs per request, nullable
   * @throws DomainValidationException if validation fails
   */
  public BatchingConfig(
      Long id,
      Long provenanceId,
      String operationType,
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
    this.effectiveFrom = effectiveFrom;
    this.effectiveTo = effectiveTo;
    this.detailFetchBatchSize = detailFetchBatchSize;
    this.idsParamName = idsParamName != null ? idsParamName.trim() : null;
    this.idsJoinDelimiter = idsJoinDelimiter != null ? idsJoinDelimiter.trim() : null;
    this.maxIdsPerRequest = maxIdsPerRequest;
  }
}
