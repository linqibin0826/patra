package com.patra.egress.app.usecase.externalcall;

import com.patra.egress.domain.model.vo.ResponseEnvelope;

import java.time.Duration;

/**
 * External call result
 * Contains response envelope, duration, retry count, and trace ID
 *
 * @param envelope response envelope
 * @param duration call duration
 * @param retryCount actual retry count
 * @param traceId distributed tracing ID
 * @author linqibin
 * @since 0.1.0
 */
public record ExternalCallResult(
    ResponseEnvelope envelope,
    Duration duration,
    int retryCount,
    String traceId
) {
    /**
     * Constructor ensuring immutability
     */
    public ExternalCallResult {
        if (envelope == null) {
            throw new IllegalArgumentException("Response envelope cannot be null");
        }
        if (duration == null || duration.isNegative()) {
            throw new IllegalArgumentException("Duration must be non-negative");
        }
        if (retryCount < 0) {
            throw new IllegalArgumentException("Retry count cannot be negative");
        }
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("Trace ID cannot be null or blank");
        }
    }
}
