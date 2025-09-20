package com.patra.registry.domain.model.vo.provenance;

import java.time.Instant;

/**
 * {@code reg_prov_batching_cfg} 的领域值对象。
 */
public record BatchingConfig(
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
    public BatchingConfig(Long id,
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
                          String requestTemplateJson) {
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

        this.id = id;
        this.provenanceId = provenanceId;
        this.scopeCode = scopeCode.trim();
        this.taskType = taskType != null ? taskType.trim() : null;
        this.taskTypeKey = taskTypeKey != null ? taskTypeKey.trim() : "ALL";
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.detailFetchBatchSize = detailFetchBatchSize;
        this.endpointId = endpointId;
        this.credentialName = credentialName != null ? credentialName.trim() : null;
        this.idsParamName = idsParamName != null ? idsParamName.trim() : null;
        this.idsJoinDelimiter = idsJoinDelimiter != null ? idsJoinDelimiter.trim() : null;
        this.maxIdsPerRequest = maxIdsPerRequest;
        this.preferCompactPayload = preferCompactPayload;
        this.payloadCompressStrategyCode = payloadCompressStrategyCode.trim();
        this.appParallelismDegree = appParallelismDegree;
        this.perHostConcurrencyLimit = perHostConcurrencyLimit;
        this.httpConnPoolSize = httpConnPoolSize;
        this.backpressureStrategyCode = backpressureStrategyCode.trim();
        this.requestTemplateJson = requestTemplateJson;
    }
}
