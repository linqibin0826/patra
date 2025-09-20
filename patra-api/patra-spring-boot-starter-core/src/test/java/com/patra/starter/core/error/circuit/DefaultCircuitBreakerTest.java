package com.patra.starter.core.error.circuit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DefaultCircuitBreaker.
 * 
 * @author linqibin
 * @since 0.1.0
 */
class DefaultCircuitBreakerTest {
    
    private DefaultCircuitBreaker circuitBreaker;
    
    @BeforeEach
    void setUp() {
        circuitBreaker = new DefaultCircuitBreaker(
            "test-circuit",
            3, // failureThreshold
            0.5, // failureRateThreshold (50%)
            Duration.ofMillis(100), // timeout
            20 // slidingWindowSize
        );
    }
    
    @Test
    void execute_ShouldReturnResultWhenClosed() {
        // Given
        Supplier<String> supplier = () -> "success";
        
        // When
        String result = circuitBreaker.execute(supplier);
        
        // Then
        assertEquals("success", result);
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
    }
    
    @Test
    void execute_ShouldOpenCircuitAfterConsecutiveFailures() {
        // Given
        Supplier<String> failingSupplier = () -> {
            throw new RuntimeException("Test failure");
        };
        
        // When - Execute failing supplier multiple times
        for (int i = 0; i < 3; i++) {
            assertThrows(RuntimeException.class, () -> circuitBreaker.execute(failingSupplier));
        }
        
        // Then - Circuit should be open
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        
        // And subsequent calls should throw CircuitBreakerOpenException
        assertThrows(CircuitBreakerOpenException.class, () -> 
            circuitBreaker.execute(() -> "should not execute"));
    }
    
    @Test
    void execute_ShouldTransitionToHalfOpenAfterTimeout() throws InterruptedException {
        // Given - Open the circuit
        Supplier<String> failingSupplier = () -> {
            throw new RuntimeException("Test failure");
        };
        
        for (int i = 0; i < 3; i++) {
            assertThrows(RuntimeException.class, () -> circuitBreaker.execute(failingSupplier));
        }
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        
        // When - Wait for timeout
        Thread.sleep(150); // Wait longer than 100ms timeout
        
        // Then - Next execution should transition to HALF_OPEN
        String result = circuitBreaker.execute(() -> "recovery");
        assertEquals("recovery", result);
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
    }
    
    @Test
    void execute_ShouldOpenCircuitBasedOnFailureRate() {
        // Given - Circuit breaker with 50% failure rate threshold
        Supplier<String> mixedSupplier = new Supplier<String>() {
            private int callCount = 0;
            
            @Override
            public String get() {
                callCount++;
                if (callCount % 2 == 0) {
                    throw new RuntimeException("Failure");
                }
                return "success";
            }
        };
        
        // When - Execute enough calls to trigger failure rate check
        for (int i = 0; i < 12; i++) { // 6 successes, 6 failures = 50% failure rate
            try {
                circuitBreaker.execute(mixedSupplier);
            } catch (RuntimeException e) {
                // Expected for failing calls
            }
        }
        
        // Then - Circuit should be open due to failure rate
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
    }
    
    @Test
    void getFailureRate_ShouldCalculateCorrectRate() {
        // Given
        Supplier<String> mixedSupplier = new Supplier<String>() {
            private int callCount = 0;
            
            @Override
            public String get() {
                callCount++;
                if (callCount <= 3) {
                    return "success";
                } else {
                    throw new RuntimeException("Failure");
                }
            }
        };
        
        // When - Execute 3 successes and 2 failures
        for (int i = 0; i < 5; i++) {
            try {
                circuitBreaker.execute(mixedSupplier);
            } catch (RuntimeException e) {
                // Expected for failing calls
            }
        }
        
        // Then - Failure rate should be 40% (2 failures out of 5 calls)
        assertEquals(0.4, circuitBreaker.getFailureRate(), 0.01);
    }
    
    @Test
    void getRecentCallCount_ShouldReturnCorrectCount() {
        // Given
        Supplier<String> supplier = () -> "success";
        
        // When
        for (int i = 0; i < 5; i++) {
            circuitBreaker.execute(supplier);
        }
        
        // Then
        assertEquals(5, circuitBreaker.getRecentCallCount());
    }
    
    @Test
    void forceOpen_ShouldOpenCircuit() {
        // Given
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
        
        // When
        circuitBreaker.forceOpen();
        
        // Then
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        assertThrows(CircuitBreakerOpenException.class, () -> 
            circuitBreaker.execute(() -> "should not execute"));
    }
    
    @Test
    void forceClose_ShouldCloseCircuit() {
        // Given - Open the circuit first
        circuitBreaker.forceOpen();
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        
        // When
        circuitBreaker.forceClose();
        
        // Then
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
        String result = circuitBreaker.execute(() -> "success");
        assertEquals("success", result);
    }
}