package com.patra.egress.api.dto;

/**
 * Retry advice DTO
 *
 * <p>Provides retry guidance based on response status code and headers.
 * Helps callers determine if they should retry the request and how long to wait.</p>
 *
 * @param retryable whether the request can be retried
 * @param suggestedDelaySeconds suggested delay before retry (in seconds)
 * @param reason reason for retry advice
 * @author linqibin
 * @since 0.1.0
 */
public record RetryAdviceDTO(
    boolean retryable,
    long suggestedDelaySeconds,
    String reason
) {
}
