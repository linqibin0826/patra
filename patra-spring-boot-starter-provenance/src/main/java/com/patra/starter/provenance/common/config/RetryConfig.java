package com.patra.starter.provenance.common.config;

/**
 * Retry configuration
 *
 * @author linqibin
 * @since 0.1.0
 */
public record RetryConfig(
    Integer maxRetryTimes,
    Integer initialDelayMillis
) {
}
