package com.patra.starter.provenance.common.config;

/**
 * Batching configuration
 *
 * @author linqibin
 * @since 0.1.0
 */
public record BatchingConfig(
    Integer detailFetchBatchSize,
    Integer maxIdsPerRequest
) {
}
