package com.patra.egress.api.error;

/**
 * Exception thrown when circuit breaker is open
 *
 * <p>Indicates that the circuit breaker has opened due to repeated
 * failures, and requests are being rejected to prevent cascading failures.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public class CircuitBreakerOpenException extends EgressException {

    /**
     * Constructs a circuit breaker open exception
     *
     * @param message circuit breaker error message
     */
    public CircuitBreakerOpenException(String message) {
        super(EgressErrors.CIRCUIT_BREAKER_OPEN, message);
    }

    /**
     * Constructs a circuit breaker open exception with cause
     *
     * @param message circuit breaker error message
     * @param cause underlying cause
     */
    public CircuitBreakerOpenException(String message, Throwable cause) {
        super(EgressErrors.CIRCUIT_BREAKER_OPEN, message, cause);
    }
}
