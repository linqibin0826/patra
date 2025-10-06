package com.patra.registry.domain.model.read.expr;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;

/**
 * Query view for API parameter mappings.
 *
 * <p>Read-optimized projection for querying standard key to provider parameter mappings.
 *
 * @author linqibin
 * @since 0.1.0
 */
public record ApiParamMappingQuery(
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
    public ApiParamMappingQuery {
        DomainValidationException.positive(provenanceId, "Provenance id");
        stdKey = DomainValidationException.notBlank(stdKey, "Standard key");
        providerParamName = DomainValidationException.notBlank(providerParamName, "Provider param name");
        operationType = operationType != null ? operationType.trim() : null;
        endpointName = endpointName != null ? endpointName.trim() : null;
        transformCode = transformCode != null ? transformCode.trim() : null;
        DomainValidationException.nonNull(effectiveFrom, "Effective from");
    }
}
