package com.patra.registry.domain.model.read.provenance;

import com.patra.registry.domain.exception.DomainValidationException;


/**
 * Provenance 聚合配置查询视图。
 */
public record ProvenanceConfigQuery(
        ProvenanceQuery provenance,
        WindowOffsetQuery windowOffset,
        PaginationConfigQuery pagination,
        HttpConfigQuery http,
        BatchingConfigQuery batching,
        RetryConfigQuery retry,
        RateLimitConfigQuery rateLimit
) {
    public ProvenanceConfigQuery {
        if (provenance == null) {
            throw new DomainValidationException("Provenance cannot be null");
        }
    }
}
