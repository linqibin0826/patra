// api/rest/dto/response/LiteratureProvenanceConfigResponse.java
package com.patra.registry.api.rpc.dto;

import com.patra.common.enums.ProvenanceCode;

import java.util.Map;

public record LiteratureProvenanceConfigApiResp(
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
        Map<String, String> headers       // 仅公共头，敏感认证头勿直出
) {
}
