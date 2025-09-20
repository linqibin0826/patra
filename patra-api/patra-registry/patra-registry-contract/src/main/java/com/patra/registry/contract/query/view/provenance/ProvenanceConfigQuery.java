package com.patra.registry.contract.query.view.provenance;

import java.util.List;

/**
 * Provenance 聚合配置查询视图。
 */
public record ProvenanceConfigQuery(
        ProvenanceQuery provenance,
        EndpointDefinitionQuery endpoint,
        WindowOffsetQuery windowOffset,
        PaginationConfigQuery pagination,
        HttpConfigQuery http,
        BatchingConfigQuery batching,
        RetryConfigQuery retry,
        RateLimitConfigQuery rateLimit,
        List<CredentialQuery> credentials
) {
    public ProvenanceConfigQuery {
        if (provenance == null) {
            throw new IllegalArgumentException("Provenance cannot be null");
        }
        credentials = credentials == null ? List.of() : List.copyOf(credentials);
    }
}
