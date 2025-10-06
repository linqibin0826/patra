package com.patra.registry.api.rpc.dto.expr;

import java.time.Instant;

/**
 * Response DTO describing API parameter mappings for expression evaluation.
 *
 * <p>Field descriptions:
 * <ol>
 *   <li>provenanceId - internal provenance identifier backing the mapping</li>
 *   <li>operationType - operation discriminator (e.g., HARVEST/UPDATE)</li>
 *   <li>endpointName - endpoint discriminator the mapping applies to</li>
 *   <li>stdKey - standardized parameter key used inside the engine</li>
 *   <li>providerParamName - original provider parameter name</li>
 *   <li>transformCode - optional transformation identifier applied to values</li>
 *   <li>notesJson - structured notes payload for downstream diagnostics</li>
 *   <li>effectiveFrom - timestamp from which the mapping becomes effective</li>
 *   <li>effectiveTo - timestamp until which the mapping remains effective</li>
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
        Instant effectiveTo
) {
}
