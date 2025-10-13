package com.patra.egress.api.dto;

import java.util.Map;

/**
 * Response envelope DTO
 *
 * <p>Unified response structure that wraps external service responses with standardized metadata
 * (rate limit status, retry advice, etc.).
 *
 * @param success whether the call succeeded (based on HTTP status code)
 * @param statusCode HTTP status code from external service
 * @param headers filtered response headers (whitelist-based)
 * @param body original response body from external service
 * @param bodyHash SHA-256 hash of response body (for deduplication/audit)
 * @param rateLimitStatus rate limit status information
 * @param retryAdvice retry guidance for the caller
 * @param snapshotMode snapshot mode indicator (e.g., "META_PLUS_BODY")
 * @author linqibin
 * @since 0.1.0
 */
public record ResponseEnvelopeDTO(
    boolean success,
    int statusCode,
    Map<String, String> headers,
    String body,
    String bodyHash,
    RateLimitStatusDTO rateLimitStatus,
    RetryAdviceDTO retryAdvice,
    String snapshotMode) {
  /** Compact constructor for immutability. */
  public ResponseEnvelopeDTO {
    // Create an immutable defensive copy of the response headers.
    headers = headers != null ? Map.copyOf(headers) : Map.of();
  }
}
