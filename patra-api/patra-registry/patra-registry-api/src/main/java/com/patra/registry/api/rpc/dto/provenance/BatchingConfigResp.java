package com.patra.registry.api.rpc.dto.provenance;

import java.time.Instant;

/**
 * 批量配置响应 DTO。
 */
public record BatchingConfigResp(
        Long id,
        Long provenanceId,
        String scopeCode,
        String taskType,
        String taskTypeKey,
        Instant effectiveFrom,
        Instant effectiveTo,
        Integer detailFetchBatchSize,
        Long endpointId,
        String credentialName,
        String idsParamName,
        String idsJoinDelimiter,
        Integer maxIdsPerRequest,
        boolean preferCompactPayload,
        String payloadCompressStrategyCode,
        Integer appParallelismDegree,
        Integer perHostConcurrencyLimit,
        Integer httpConnPoolSize,
        String backpressureStrategyCode,
        String requestTemplateJson
) {
}
