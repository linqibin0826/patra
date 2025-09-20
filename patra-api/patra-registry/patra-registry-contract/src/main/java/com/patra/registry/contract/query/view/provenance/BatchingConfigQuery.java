package com.patra.registry.contract.query.view.provenance;

import java.time.Instant;

/**
 * 批量抓取与请求成型配置查询视图。
 */
public record BatchingConfigQuery(
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
    public BatchingConfigQuery {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Batching config id must be positive");
        }
        if (provenanceId == null || provenanceId <= 0) {
            throw new IllegalArgumentException("Provenance id must be positive");
        }
        if (scopeCode == null || scopeCode.isBlank()) {
            throw new IllegalArgumentException("Scope code cannot be blank");
        }
        if (payloadCompressStrategyCode == null || payloadCompressStrategyCode.isBlank()) {
            throw new IllegalArgumentException("Payload compress strategy code cannot be blank");
        }
        if (backpressureStrategyCode == null || backpressureStrategyCode.isBlank()) {
            throw new IllegalArgumentException("Backpressure strategy code cannot be blank");
        }
        if (effectiveFrom == null) {
            throw new IllegalArgumentException("Effective from cannot be null");
        }
        scopeCode = scopeCode.trim();
        taskType = taskType != null ? taskType.trim() : null;
        taskTypeKey = taskTypeKey != null ? taskTypeKey.trim() : "ALL";
        credentialName = credentialName != null ? credentialName.trim() : null;
        idsParamName = idsParamName != null ? idsParamName.trim() : null;
        idsJoinDelimiter = idsJoinDelimiter != null ? idsJoinDelimiter.trim() : null;
        payloadCompressStrategyCode = payloadCompressStrategyCode.trim();
        backpressureStrategyCode = backpressureStrategyCode.trim();
    }
}
