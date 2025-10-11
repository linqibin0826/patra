package com.patra.egress.domain.port;

import java.util.function.Supplier;

/**
 * Domain port that exposes circuit breaker functionality to the application layer.
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface CircuitBreakerPort {
    
    /**
     * Enumeration describing the circuit breaker state machine.
     */
    enum CircuitBreakerState {
        /** Closed state (healthy). */
        CLOSED,
        /** Open state (calls are rejected). */
        OPEN,
        /** Half-open state (probing recovery). */
        HALF_OPEN
    }

    /**
     * Execute an operation while guarded by a circuit breaker instance.
     *
     * @param key       circuit breaker identifier
     * @param supplier  operation to execute when the breaker allows
     * @param threshold failure threshold that opens the breaker
     * @param <T>       return type of the guarded operation
     * @return result of the supplied operation
     */
    <T> T executeWithCircuitBreaker(String key, Supplier<T> supplier, int threshold);
    
    /**
     * Obtain the current state for the circuit breaker identified by the key.
     *
     * @param key circuit breaker identifier
     * @return current state of the breaker
     */
    CircuitBreakerState getState(String key);
}
