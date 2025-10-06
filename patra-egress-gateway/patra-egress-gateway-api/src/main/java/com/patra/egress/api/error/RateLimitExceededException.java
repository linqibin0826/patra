package com.patra.egress.api.error;

/**
 * Exception thrown when rate limit is exceeded
 *
 * <p>Indicates that the gateway has reached its rate limit threshold
 * and cannot accept additional requests at this time.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public class RateLimitExceededException extends EgressException {

    /**
     * Constructs a rate limit exceeded exception
     *
     * @param message rate limit error message
     */
    public RateLimitExceededException(String message) {
        super(EgressErrors.RATE_LIMIT_EXCEEDED, message);
    }

    /**
     * Constructs a rate limit exceeded exception with cause
     *
     * @param message rate limit error message
     * @param cause underlying cause
     */
    public RateLimitExceededException(String message, Throwable cause) {
        super(EgressErrors.RATE_LIMIT_EXCEEDED, message, cause);
    }
}
