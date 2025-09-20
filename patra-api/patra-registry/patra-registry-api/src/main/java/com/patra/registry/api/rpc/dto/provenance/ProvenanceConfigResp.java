package com.patra.registry.api.rpc.dto.provenance;

import java.util.List;

/**
 * Provenance 配置聚合响应 DTO。
 */
public record ProvenanceConfigResp(
        ProvenanceResp provenance,
        EndpointDefinitionResp endpoint,
        WindowOffsetResp windowOffset,
        PaginationConfigResp pagination,
        HttpConfigResp http,
        BatchingConfigResp batching,
        RetryConfigResp retry,
        RateLimitConfigResp rateLimit,
        List<CredentialResp> credentials
) {
}
