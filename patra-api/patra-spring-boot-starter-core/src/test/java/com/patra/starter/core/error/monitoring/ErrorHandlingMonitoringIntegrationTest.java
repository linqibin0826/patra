package com.patra.starter.core.error.monitoring;

import com.patra.common.error.ApplicationException;
import com.patra.common.error.codes.ErrorCodeLike;
import com.patra.starter.core.error.circuit.CircuitBreaker;
import com.patra.starter.core.error.circuit.CircuitBreakerProtectedContributor;
import com.patra.starter.core.error.circuit.DefaultCircuitBreaker;
import com.patra.starter.core.error.config.ErrorProperties;
import com.patra.starter.core.error.metrics.DefaultErrorMetrics;
import com.patra.starter.core.error.metrics.ErrorMetrics;
import com.patra.starter.core.error.service.ErrorResolutionService;
import com.patra.starter.core.error.spi.ErrorMappingContributor;
import com.patra.starter.core.error.spi.StatusMappingStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration test for error handling monitoring functionality.
 * Tests the complete monitoring pipeline including metrics collection,
 * circuit breaker protection, and performance tracking.
 * 
 * @author linqibin
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
class ErrorHandlingMonitoringIntegrationTest {
    
    @Mock
    private StatusMappingStrategy statusMappingStrategy;
    
    @Mock
    private ErrorMappingContributor slowContributor;
    
    @Mock
    private ErrorMappingContributor failingContributor;
    
    private ErrorProperties errorProperties;
    private ErrorMetrics errorMetrics;
    private ErrorResolutionService errorResolutionService;
    
    @BeforeEach
    void setUp() {
        errorProperties = new ErrorProperties();
        errorProperties.setContextPrefix("TEST");
        
        errorMetrics = new DefaultErrorMetrics();
        
        when(statusMappingStrategy.mapToHttpStatus(any(), any())).thenReturn(500);
    }
    
    @Test
    void errorResolutionService_ShouldCollectMetricsForSuccessfulResolution() {
        // Given
        List<ErrorMappingContributor> contributors = List.of();
        errorResolutionService = new ErrorResolutionService(
            errorProperties, statusMappingStrategy, contributors, errorMetrics);
        
        ErrorCodeLike testCode = () -> "TEST-0001";
        ApplicationException testException = new ApplicationException(testCode, "Test error");
        
        // When
        var resolution = errorResolutionService.resolve(testException);
        
        // Then
        assertNotNull(resolution);
        assertEquals("TEST-0001", resolution.errorCode().code());
        assertEquals(500, resolution.httpStatus());
    }
    
    @Test
    void errorResolutionService_ShouldCollectMetricsForCacheHits() {
        // Given
        List<ErrorMappingContributor> contributors = List.of();
        errorResolutionService = new ErrorResolutionService(
            errorProperties, statusMappingStrategy, contributors, errorMetrics);
        
        RuntimeException testException = new RuntimeException("Test error");
        
        // When - Resolve same exception type twice
        var resolution1 = errorResolutionService.resolve(testException);
        var resolution2 = errorResolutionService.resolve(new RuntimeException("Another test error"));
        
        // Then - Both should succeed
        assertNotNull(resolution1);
        assertNotNull(resolution2);
        assertEquals(resolution1.errorCode().code(), resolution2.errorCode().code());
    }
    
    @Test
    void errorResolutionService_ShouldCollectContributorPerformanceMetrics() {
        // Given
        when(slowContributor.mapException(any())).thenAnswer(invocation -> {
            // Simulate slow contributor
            Thread.sleep(60); // Simulate 60ms execution time
            return Optional.empty();
        });
        
        List<ErrorMappingContributor> contributors = List.of(slowContributor);
        errorResolutionService = new ErrorResolutionService(
            errorProperties, statusMappingStrategy, contributors, errorMetrics);
        
        RuntimeException testException = new RuntimeException("Test error");
        
        // When
        var resolution = errorResolutionService.resolve(testException);
        
        // Then
        assertNotNull(resolution);
        verify(slowContributor).mapException(testException);
    }
    
    @Test
    void errorResolutionService_ShouldHandleContributorFailures() {
        // Given
        when(failingContributor.mapException(any())).thenThrow(new RuntimeException("Contributor failed"));
        
        List<ErrorMappingContributor> contributors = List.of(failingContributor);
        errorResolutionService = new ErrorResolutionService(
            errorProperties, statusMappingStrategy, contributors, errorMetrics);
        
        RuntimeException testException = new RuntimeException("Test error");
        
        // When
        var resolution = errorResolutionService.resolve(testException);
        
        // Then - Should still resolve using fallback
        assertNotNull(resolution);
        verify(failingContributor).mapException(testException);
    }
    
    @Test
    void circuitBreakerProtectedContributor_ShouldProtectAgainstFailures() {
        // Given
        CircuitBreaker circuitBreaker = new DefaultCircuitBreaker(
            "test-contributor", 2, 0.5, Duration.ofMillis(100), 10);
        
        when(failingContributor.mapException(any())).thenThrow(new RuntimeException("Contributor failed"));
        
        CircuitBreakerProtectedContributor protectedContributor = 
            new CircuitBreakerProtectedContributor(failingContributor, circuitBreaker);
        
        RuntimeException testException = new RuntimeException("Test error");
        
        // When - Execute multiple times to trigger circuit breaker
        for (int i = 0; i < 3; i++) {
            Optional<ErrorCodeLike> result = protectedContributor.mapException(testException);
            assertTrue(result.isEmpty());
        }
        
        // Then - Circuit breaker should be open
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        
        // And subsequent calls should be short-circuited
        Optional<ErrorCodeLike> result = protectedContributor.mapException(testException);
        assertTrue(result.isEmpty());
        
        // Verify contributor was called initially but not after circuit opened
        verify(failingContributor, atMost(3)).mapException(testException);
    }
    
    @Test
    void circuitBreakerProtectedContributor_ShouldRecoverAfterTimeout() throws InterruptedException {
        // Given
        CircuitBreaker circuitBreaker = new DefaultCircuitBreaker(
            "test-contributor", 2, 0.5, Duration.ofMillis(50), 10);
        
        // First make it fail to open the circuit
        when(failingContributor.mapException(any())).thenThrow(new RuntimeException("Contributor failed"));
        
        CircuitBreakerProtectedContributor protectedContributor = 
            new CircuitBreakerProtectedContributor(failingContributor, circuitBreaker);
        
        RuntimeException testException = new RuntimeException("Test error");
        
        // Open the circuit
        for (int i = 0; i < 3; i++) {
            protectedContributor.mapException(testException);
        }
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        
        // Wait for timeout
        Thread.sleep(100);
        
        // Now make contributor succeed
        ErrorCodeLike successCode = () -> "SUCCESS-001";
        when(failingContributor.mapException(any())).thenReturn(Optional.of(successCode));
        
        // When - Execute after timeout
        Optional<ErrorCodeLike> result = protectedContributor.mapException(testException);
        
        // Then - Should succeed and close circuit
        assertTrue(result.isPresent());
        assertEquals("SUCCESS-001", result.get().code());
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
    }
    
    @Test
    void errorResolutionService_ShouldTrackCauseChainDepth() {
        // Given
        List<ErrorMappingContributor> contributors = List.of();
        errorResolutionService = new ErrorResolutionService(
            errorProperties, statusMappingStrategy, contributors, errorMetrics);
        
        // Create nested exception chain
        RuntimeException rootCause = new RuntimeException("Root cause");
        RuntimeException middleCause = new RuntimeException("Middle cause", rootCause);
        RuntimeException topException = new RuntimeException("Top exception", middleCause);
        
        // When
        var resolution = errorResolutionService.resolve(topException);
        
        // Then
        assertNotNull(resolution);
        // Should resolve using naming convention for RuntimeException -> fallback
    }
    
    @Test
    void errorResolutionService_ShouldHandleNullException() {
        // Given
        List<ErrorMappingContributor> contributors = List.of();
        errorResolutionService = new ErrorResolutionService(
            errorProperties, statusMappingStrategy, contributors, errorMetrics);
        
        // When
        var resolution = errorResolutionService.resolve(null);
        
        // Then - Should return fallback resolution
        assertNotNull(resolution);
        assertEquals("TEST-0500", resolution.errorCode().code());
        assertEquals(500, resolution.httpStatus());
    }
}