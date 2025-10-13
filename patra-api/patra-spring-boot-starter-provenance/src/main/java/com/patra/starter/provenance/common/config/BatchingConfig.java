package com.patra.starter.provenance.common.config;

/**
 * Batching configuration.
 *
 * <p>Field descriptions:
 *
 * @param detailFetchBatchSize batch size used when expanding detail fetch requests
 * @param maxIdsPerRequest hard cap on identifiers included in a single API call
 * @author linqibin
 * @since 0.1.0
 */
public record BatchingConfig(Integer detailFetchBatchSize, Integer maxIdsPerRequest) {}
