package com.patra.egress.api.error;

/**
 * Exception thrown when external service call times out
 *
 * <p>Indicates that the external service did not respond within
 * the configured timeout period.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public class ExternalCallTimeoutException extends EgressException {

    /**
     * Constructs an external call timeout exception
     *
     * @param message timeout error message
     */
    public ExternalCallTimeoutException(String message) {
        super(EgressErrors.GATEWAY_TIMEOUT, message);
    }

    /**
     * Constructs an external call timeout exception with cause
     *
     * @param message timeout error message
     * @param cause underlying cause
     */
    public ExternalCallTimeoutException(String message, Throwable cause) {
        super(EgressErrors.GATEWAY_TIMEOUT, message, cause);
    }
}
