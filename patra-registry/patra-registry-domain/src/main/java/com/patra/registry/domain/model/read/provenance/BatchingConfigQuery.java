package com.patra.registry.domain.model.read.provenance;

import com.patra.registry.domain.exception.DomainValidationException;

import java.time.Instant;

/**
 * 批量抓取与请求成型配置查询视图。
 */
public record BatchingConfigQuery(
        Long id,
        Long provenanceId,
        String operationType,
        String operationTypeKey,
        Instant effectiveFrom,
        Instant effectiveTo,
        Integer detailFetchBatchSize,
        String idsParamName,
        String idsJoinDelimiter,
        Integer maxIdsPerRequest
) {
    public BatchingConfigQuery {
        if (id == null || id <= 0) {
            throw new DomainValidationException("Batching config id must be positive");
        }
        if (provenanceId == null || provenanceId <= 0) {
            throw new DomainValidationException("Provenance id must be positive");
        }
        if (effectiveFrom == null) {
            throw new DomainValidationException("Effective from cannot be null");
        }
        operationType = operationType != null ? operationType.trim() : null;
        operationTypeKey = operationTypeKey != null ? operationTypeKey.trim() : "ALL";
        idsParamName = idsParamName != null ? idsParamName.trim() : null;
        idsJoinDelimiter = idsJoinDelimiter != null ? idsJoinDelimiter.trim() : null;
    }
}
