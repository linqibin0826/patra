package com.patra.registry.domain.model.vo.provenance;

import com.patra.registry.domain.exception.DomainValidationException;
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
        DomainValidationException.positive(id, "Batching config id");
        DomainValidationException.positive(provenanceId, "Provenance id");
        String scopeTrimmed = DomainValidationException.notBlank(scopeCode, "Scope code");
        String payloadCompressTrimmed = DomainValidationException.notBlank(payloadCompressStrategyCode, "Payload compress strategy code");
        String backpressureTrimmed = DomainValidationException.notBlank(backpressureStrategyCode, "Backpressure strategy code");
        DomainValidationException.nonNull(effectiveFrom, "Effective from");

        this.id = id; // 已验证
        this.provenanceId = provenanceId; // 已验证
        this.scopeCode = scopeTrimmed;
        this.taskType = taskType != null ? taskType.trim() : null;
        this.taskTypeKey = taskTypeKey != null ? taskTypeKey.trim() : "ALL";
        this.effectiveFrom = effectiveFrom; // 非 null 已验证
        this.effectiveTo = effectiveTo;
        this.detailFetchBatchSize = detailFetchBatchSize;
        this.credentialName = credentialName != null ? credentialName.trim() : null;
        this.idsParamName = idsParamName != null ? idsParamName.trim() : null;
        this.idsJoinDelimiter = idsJoinDelimiter != null ? idsJoinDelimiter.trim() : null;
        this.maxIdsPerRequest = maxIdsPerRequest;
        this.preferCompactPayload = preferCompactPayload;
        this.payloadCompressStrategyCode = payloadCompressTrimmed;
        this.appParallelismDegree = appParallelismDegree;
        this.perHostConcurrencyLimit = perHostConcurrencyLimit;
        this.httpConnPoolSize = httpConnPoolSize;
        this.backpressureStrategyCode = backpressureTrimmed;
        this.requestTemplateJson = requestTemplateJson;
    }
}
