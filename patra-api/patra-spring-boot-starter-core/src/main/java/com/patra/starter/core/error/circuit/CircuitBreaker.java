package com.patra.starter.core.error.circuit;

import java.util.function.Supplier;

/**
 * Simple circuit breaker interface for protecting error mapping contributors
 * from cascading failures and performance issues.
 * 
 * @author linqibin
 * @since 0.1.0
 */
public interface CircuitBreaker {
    
    /**
     * Circuit breaker states.
     */
    enum State {
        /** Circuit is closed, allowing normal operation */
        CLOSED,
        /** Circuit is open, rejecting calls to prevent cascading failures */
        OPEN,
        /** Circuit is half-open, allowing limited calls to test recovery */
        HALF_OPEN
    }
    
    /**
     * Executes the given supplier with circuit breaker protection.
     * 
     * @param supplier the operation to execute
     * @param <T> the return type
     * @return the result of the operation
     * @throws CircuitBreakerOpenException if the circuit is open
     */
    <T> T execute(Supplier<T> supplier) throws CircuitBreakerOpenException;
    
    /**
     * Gets the current state of the circuit breaker.
     * 
     * @return the current state
     */
    State getState();
    
    /**
     * Gets the failure rate as a percentage (0.0 to 1.0).
     * 
     * @return the current failure rate
     */
    double getFailureRate();
    
    /**
     * Gets the number of recent calls tracked by the circuit breaker.
     * 
     * @return the number of recent calls
     */
    long getRecentCallCount();
    
    /**
     * Manually opens the circuit breaker.
     */
    void forceOpen();
    
    /**
     * Manually closes the circuit breaker.
     */
    void forceClose();
}
