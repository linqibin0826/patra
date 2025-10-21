package com.patra.starter.provenance.common.http;

/**
 * Minimal HTTP resilience configuration used by the provenance starter's simple HTTP client.
 *
 * @param timeoutSeconds request timeout in seconds (nullable → library default)
 * @param maxRetries maximum retry attempts (nullable/<=0 → no retry)
 * @param retryBackoffSeconds backoff between retries in seconds (nullable → 0)
 * @param rateLimitQps optional per-credential QPS limit (best-effort, local only)
 */
public record HttpResilienceConfig(
    Long timeoutSeconds, Integer maxRetries, Long retryBackoffSeconds, Integer rateLimitQps) {}
