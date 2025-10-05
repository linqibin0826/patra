package com.patra.registry.domain.model.read.expr;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;

/**
 * API 参数映射查询视图。
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
