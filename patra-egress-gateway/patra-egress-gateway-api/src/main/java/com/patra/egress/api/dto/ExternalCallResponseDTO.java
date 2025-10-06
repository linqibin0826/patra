package com.patra.egress.api.dto;

/**
 * External call response DTO
 *
 * <p>Complete response from an external service call through the egress gateway,
 * including the wrapped response envelope and call metadata.</p>
 *
 * @param envelope standardized response envelope with external service data
 * @param durationMs total call duration in milliseconds (including retries)
 * @param retryCount actual number of retry attempts made
 * @param traceId distributed tracing ID for correlation
 * @author linqibin
 * @since 0.1.0
 */
public record ExternalCallResponseDTO(
    ResponseEnvelopeDTO envelope,
    long durationMs,
    int retryCount,
    String traceId
) {
}
