package com.patra.registry.api.rpc.dto.expr;

import java.time.Instant;

/**
 * API 参数映射响应 DTO。
 */
public record ApiParamMappingResp(
        Long provenanceId,
        String scopeCode,
        String taskType,
        String operationCode,
        String stdKey,
        String providerParamName,
        String transformCode,
        String notesJson,
        Instant effectiveFrom,
        Instant effectiveTo
) {
}
