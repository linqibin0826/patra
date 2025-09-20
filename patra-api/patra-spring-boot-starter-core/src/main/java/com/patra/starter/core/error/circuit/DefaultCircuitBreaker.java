package com.patra.starter.core.error.circuit;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Default implementation of CircuitBreaker with configurable failure thresholds
 * and recovery timeouts. Uses a sliding window approach to track recent calls.
 * 
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
public class DefaultCircuitBreaker implements CircuitBreaker {
    
    private final String name;
    private final int failureThreshold;
    private final double failureRateThreshold;
    private final Duration timeout;
    private final int slidingWindowSize;
    
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger totalCalls = new AtomicInteger(0);
    
    /**
     * Creates a new DefaultCircuitBreaker with the specified configuration.
     * 
     * @param name the name of the circuit breaker
     * @param failureThreshold the number of consecutive failures to open the circuit
     * @param failureRateThreshold the failure rate threshold (0.0 to 1.0) to open the circuit
     * @param timeout the timeout duration before attempting to close the circuit
     * @param slidingWindowSize the size of the sliding window for tracking calls
     */
    public DefaultCircuitBreaker(String name, int failureThreshold, double failureRateThreshold, 
                               Duration timeout, int slidingWindowSize) {
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.failureRateThreshold = failureRateThreshold;
        this.timeout = timeout;
        this.slidingWindowSize = slidingWindowSize;
    }
    
    @Override
    public <T> T execute(Supplier<T> supplier) throws CircuitBreakerOpenException {
        State currentState = state.get();
        
        if (currentState == State.OPEN) {
            if (shouldAttemptReset()) {
                state.compareAndSet(State.OPEN, State.HALF_OPEN);
                log.info("Circuit breaker '{}' transitioning from OPEN to HALF_OPEN", name);
                currentState = State.HALF_OPEN;
            } else {
                throw new CircuitBreakerOpenException(name);
            }
        }
        
        try {
            T result = supplier.get();
            onSuccess();
            return result;
        } catch (Exception e) {
            onFailure();
            throw e;
        }
    }
    
    @Override
    public State getState() {
        return state.get();
    }
    
    @Override
    public double getFailureRate() {
        int total = totalCalls.get();
        if (total == 0) return 0.0;
        return (double) failureCount.get() / total;
    }
    
    @Override
    public long getRecentCallCount() {
        return totalCalls.get();
    }
    
    @Override
    public void forceOpen() {
        state.set(State.OPEN);
        lastFailureTime.set(System.currentTimeMillis());
        log.warn("Circuit breaker '{}' manually opened", name);
    }
    
    @Override
    public void forceClose() {
        state.set(State.CLOSED);
        resetCounters();
        log.info("Circuit breaker '{}' manually closed", name);
    }
    
    /**
     * Handles successful execution.
     */
    private void onSuccess() {
        incrementTotalCalls();
        successCount.incrementAndGet();
        consecutiveFailures.set(0);
        
        State currentState = state.get();
        if (currentState == State.HALF_OPEN) {
            state.compareAndSet(State.HALF_OPEN, State.CLOSED);
            log.info("Circuit breaker '{}' transitioning from HALF_OPEN to CLOSED after success", name);
            resetCounters();
        }
        
        log.debug("Circuit breaker '{}' recorded success, state: {}", name, state.get());
    }
    
    /**
     * Handles failed execution.
     */
    private void onFailure() {
        incrementTotalCalls();
        failureCount.incrementAndGet();
        int failures = consecutiveFailures.incrementAndGet();
        lastFailureTime.set(System.currentTimeMillis());
        
        State currentState = state.get();
        
        // Check if we should open the circuit
        if (currentState != State.OPEN && shouldOpenCircuit(failures)) {
            state.compareAndSet(currentState, State.OPEN);
            log.warn("Circuit breaker '{}' opening due to failures: consecutive={}, rate={}, state: {}", 
                    name, failures, getFailureRate(), State.OPEN);
        }
        
        log.debug("Circuit breaker '{}' recorded failure, consecutive failures: {}, state: {}", 
                 name, failures, state.get());
    }
    
    /**
     * Increments total calls and maintains sliding window.
     */
    private void incrementTotalCalls() {
        int total = totalCalls.incrementAndGet();
        
        // Reset counters when sliding window is full to maintain recent statistics
        if (total >= slidingWindowSize) {
            int currentSuccess = successCount.get();
            int currentFailure = failureCount.get();
            
            // Keep proportional counts for the new window
            int newSuccess = currentSuccess / 2;
            int newFailure = currentFailure / 2;
            
            successCount.set(newSuccess);
            failureCount.set(newFailure);
            totalCalls.set(newSuccess + newFailure);
            
            log.debug("Circuit breaker '{}' sliding window reset, new totals: success={}, failure={}", 
                     name, newSuccess, newFailure);
        }
    }
    
    /**
     * Determines if the circuit should be opened based on failure criteria.
     */
    private boolean shouldOpenCircuit(int consecutiveFailures) {
        // Open if consecutive failures exceed threshold
        if (consecutiveFailures >= failureThreshold) {
            return true;
        }
        
        // Open if failure rate exceeds threshold and we have enough data
        if (totalCalls.get() >= 10) { // Minimum calls before considering failure rate
            return getFailureRate() >= failureRateThreshold;
        }
        
        return false;
    }
    
    /**
     * Determines if we should attempt to reset the circuit from OPEN to HALF_OPEN.
     */
    private boolean shouldAttemptReset() {
        long lastFailure = lastFailureTime.get();
        long timeoutMillis = timeout.toMillis();
        return System.currentTimeMillis() - lastFailure >= timeoutMillis;
    }
    
    /**
     * Resets all counters.
     */
    private void resetCounters() {
        successCount.set(0);
        failureCount.set(0);
        totalCalls.set(0);
        consecutiveFailures.set(0);
    }
}