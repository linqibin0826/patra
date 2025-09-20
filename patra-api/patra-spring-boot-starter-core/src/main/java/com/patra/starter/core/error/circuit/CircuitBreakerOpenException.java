package com.patra.starter.core.error.circuit;

/**
 * Exception thrown when a circuit breaker is open and rejecting calls.
 * 
 * @author linqibin
 * @since 0.1.0
 */
public class CircuitBreakerOpenException extends RuntimeException {
    
    private final String circuitBreakerName;
    
    /**
     * Creates a new CircuitBreakerOpenException.
     * 
     * @param circuitBreakerName the name of the circuit breaker that is open
     */
    public CircuitBreakerOpenException(String circuitBreakerName) {
        super("Circuit breaker '" + circuitBreakerName + "' is open");
        this.circuitBreakerName = circuitBreakerName;
    }
    
    /**
     * Gets the name of the circuit breaker that is open.
     * 
     * @return the circuit breaker name
     */
    public String getCircuitBreakerName() {
        return circuitBreakerName;
    }
}