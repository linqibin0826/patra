package com.patra.registry.api.rpc.dto.provenance;

import java.time.Instant;

/**
 * 端点定义响应 DTO。
 */
public record EndpointDefinitionResp(
        Long id,
        Long provenanceId,
        String scopeCode,
        String taskType,
        String taskTypeKey,
        String endpointName,
        String endpointUsageCode,
        String httpMethodCode,
        String pathTemplate,
        String defaultQueryParamsJson,
        String defaultBodyPayloadJson,
        String requestContentType,
        boolean authRequired,
        String credentialHintName,
        String pageParamName,
        String pageSizeParamName,
        String cursorParamName,
        String idsParamName,
        Instant effectiveFrom,
        Instant effectiveTo
) {
}
