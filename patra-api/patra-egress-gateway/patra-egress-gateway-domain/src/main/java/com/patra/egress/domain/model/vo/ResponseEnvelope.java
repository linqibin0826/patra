package com.patra.egress.domain.model.vo;

import java.util.Map;

/**
 * Unified response envelope returned by the egress gateway.
 *
 * @param success indicates whether the call succeeded (based on HTTP status code)
 * @param statusCode HTTP status code returned by the provider
 * @param headers response headers filtered through the whitelist
 * @param body provider response body
 * @param bodyHash SHA-256 hash of the response body for auditing
 * @param rateLimitStatus gateway and provider rate limit information
 * @param retryAdvice retry recommendation for the caller
 * @param snapshotMode snapshot mode indicator (e.g. metadata only vs. metadata plus body)
 * @author linqibin
 * @since 0.1.0
 */
public record ResponseEnvelope(
    boolean success,
    int statusCode,
    Map<String, String> headers,
    String body,
    String bodyHash,
    RateLimitStatus rateLimitStatus,
    RetryAdvice retryAdvice,
    String snapshotMode) {
  /** Canonical constructor that ensures header immutability. */
  public ResponseEnvelope {
    // Create an immutable defensive copy of the headers to avoid mutation.
    headers = headers != null ? Map.copyOf(headers) : Map.of();
  }
}
