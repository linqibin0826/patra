package com.patra.registry.api.rpc.dto.provenance;

/**
 * Aggregated response DTO bundling all provenance configuration dimensions.
 *
 * <p>Field descriptions:
 * <ol>
 *   <li>provenance - baseline provenance metadata</li>
 *   <li>windowOffset - window and offset configuration (nullable)</li>
 *   <li>pagination - pagination or cursor configuration (nullable)</li>
 *   <li>http - HTTP baseline configuration (nullable)</li>
 *   <li>batching - batching and request shaping configuration (nullable)</li>
 *   <li>retry - retry and backoff configuration (nullable)</li>
 *   <li>rateLimit - rate limiting and concurrency configuration (nullable)</li>
 * </ol>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record ProvenanceConfigResp(
        ProvenanceResp provenance,
        WindowOffsetResp windowOffset,
        PaginationConfigResp pagination,
        HttpConfigResp http,
        BatchingConfigResp batching,
        RetryConfigResp retry,
        RateLimitConfigResp rateLimit
) {
}
