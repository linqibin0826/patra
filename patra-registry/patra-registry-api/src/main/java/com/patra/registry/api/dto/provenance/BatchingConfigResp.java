package com.patra.registry.api.dto.provenance;

import java.time.Instant;

/**
 * Batching configuration for provenance API requests.
 *
 * <p>Field descriptions:
 *
 * <ol>
 *   <li>id - primary identifier of the batching configuration row
 *   <li>provenanceId - provenance owning the configuration
 *   <li>operationType - operation discriminator the configuration applies to
 *   <li>effectiveFrom - timestamp from which the configuration becomes effective
 *   <li>effectiveTo - timestamp until which the configuration remains effective
 *   <li>detailFetchBatchSize - default batch size used when fetching detail IDs
 *   <li>idsParamName - request parameter name carrying batched IDs
 *   <li>idsJoinDelimiter - delimiter used when joining IDs into a single value
 *   <li>maxIdsPerRequest - maximum IDs permitted per outbound request
 * </ol>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record BatchingConfigResp(
    Long id,
    Long provenanceId,
    String operationType,
    Instant effectiveFrom,
    Instant effectiveTo,
    Integer detailFetchBatchSize,
    String idsParamName,
    String idsJoinDelimiter,
    Integer maxIdsPerRequest) {}
