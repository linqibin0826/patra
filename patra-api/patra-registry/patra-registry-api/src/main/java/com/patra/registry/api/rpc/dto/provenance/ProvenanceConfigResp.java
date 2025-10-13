package com.patra.registry.api.rpc.dto.provenance;

/**
 * Aggregated response DTO bundling all provenance configuration dimensions.
 *
 * <p>Field descriptions:
 *
 * <ol>
 *   <li>provenance - baseline provenance metadata
 *   <li>windowOffset - window and offset configuration (nullable)
 *   <li>pagination - pagination or cursor configuration (nullable)
 *   <li>http - HTTP baseline configuration (nullable)
 *   <li>batching - batching and request shaping configuration (nullable)
 *   <li>retry - retry and backoff configuration (nullable)
 *   <li>rateLimit - rate limiting and concurrency configuration (nullable)
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
    RateLimitConfigResp rateLimit) {}
