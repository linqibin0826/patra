package com.patra.registry.api.dto.expr;

import java.time.Instant;

/**
 * API parameter mappings for expression evaluation.
 *
 * <p>Field descriptions:
 *
 * <ol>
 *   <li>provenanceId - internal provenance identifier backing the mapping
 *   <li>operationType - operation discriminator (e.g., HARVEST/UPDATE)
 *   <li>endpointName - endpoint discriminator the mapping applies to
 *   <li>stdKey - standardized parameter key used inside the engine
 *   <li>providerParamName - original provider parameter name
 *   <li>transformCode - optional transformation identifier applied to values
 *   <li>notesJson - structured notes payload for downstream diagnostics
 *   <li>effectiveFrom - timestamp from which the mapping becomes effective
 *   <li>effectiveTo - timestamp until which the mapping remains effective
 * </ol>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record ApiParamMappingResp(
    Long provenanceId,
    String operationType,
    String endpointName,
    String stdKey,
    String providerParamName,
    String transformCode,
    String notesJson,
    Instant effectiveFrom,
    Instant effectiveTo) {}
