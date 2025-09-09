// patra-registry-contract / query/view/LiteratureProvenanceConfigView.java
package com.patra.registry.contract.query.view;

import com.patra.common.enums.ProvenanceCode;

import java.util.Map;

public record LiteratureProvenanceConfigView(
        Long provenanceId,
        ProvenanceCode provenanceCode,
        String timezone,
        Integer retryMax,
        Integer backoffMs,
        Integer rateLimitPerSec,
        Integer searchPageSize,
        Integer fetchBatchSize,
        Integer maxSearchIdsPerWindow,
        Integer overlapDays,
        Double retryJitter,
        Boolean enableAccess,
        String dateFieldDefault,
        String baseUrl,
        Map<String, String> headers
) {
}
