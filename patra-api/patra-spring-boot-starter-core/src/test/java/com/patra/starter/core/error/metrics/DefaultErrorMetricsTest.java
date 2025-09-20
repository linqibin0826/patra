package com.patra.starter.core.error.metrics;

import com.patra.common.error.codes.ErrorCodeLike;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DefaultErrorMetrics.
 * 
 * @author linqibin
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
class DefaultErrorMetricsTest {
    
    private DefaultErrorMetrics errorMetrics;
    private ErrorCodeLike testErrorCode;
    
    @BeforeEach
    void setUp() {
        errorMetrics = new DefaultErrorMetrics();
        testErrorCode = () -> "TEST-0001";
    }
    
    @Test
    void recordResolutionTime_ShouldLogPerformanceMetrics() {
        // Given
        Class<?> exceptionClass = RuntimeException.class;
        long resolutionTime = 50L;
        boolean cacheHit = false;
        
        // When & Then - Should not throw exception
        assertDoesNotThrow(() -> 
            errorMetrics.recordResolutionTime(exceptionClass, testErrorCode, resolutionTime, cacheHit)
        );
    }
    
    @Test
    void recordResolutionTime_ShouldLogSlowResolution() {
        // Given
        Class<?> exceptionClass = RuntimeException.class;
        long slowResolutionTime = 150L; // Above 100ms threshold
        boolean cacheHit = false;
        
        // When & Then - Should not throw exception
        assertDoesNotThrow(() -> 
            errorMetrics.recordResolutionTime(exceptionClass, testErrorCode, slowResolutionTime, cacheHit)
        );
    }
    
    @Test
    void recordErrorCodeDistribution_ShouldTrackDistribution() {
        // Given
        String serviceName = "TEST";
        int httpStatus = 404;
        
        // When & Then - Should not throw exception
        assertDoesNotThrow(() -> 
            errorMetrics.recordErrorCodeDistribution(testErrorCode, httpStatus, serviceName)
        );
    }
    
    @Test
    void recordCacheHitMiss_ShouldTrackCachePerformance() {
        // Given
        Class<?> exceptionClass = RuntimeException.class;
        
        // When - Record multiple hits and misses
        for (int i = 0; i < 10; i++) {
            errorMetrics.recordCacheHitMiss(exceptionClass, i % 2 == 0); // Alternate hit/miss
        }
        
        // Then - Should not throw exception
        assertDoesNotThrow(() -> 
            errorMetrics.recordCacheHitMiss(exceptionClass, true)
        );
    }
    
    @Test
    void recordContributorPerformance_ShouldTrackContributorStats() {
        // Given
        Class<?> contributorClass = Object.class;
        boolean success = true;
        long executionTime = 25L;
        
        // When & Then - Should not throw exception
        assertDoesNotThrow(() -> 
            errorMetrics.recordContributorPerformance(contributorClass, success, executionTime)
        );
    }
    
    @Test
    void recordContributorPerformance_ShouldLogSlowContributor() {
        // Given
        Class<?> contributorClass = Object.class;
        boolean success = true;
        long slowExecutionTime = 75L; // Above 50ms threshold
        
        // When & Then - Should not throw exception
        assertDoesNotThrow(() -> 
            errorMetrics.recordContributorPerformance(contributorClass, success, slowExecutionTime)
        );
    }
    
    @Test
    void recordContributorPerformance_ShouldTrackSuccessRate() {
        // Given
        Class<?> contributorClass = Object.class;
        
        // When - Record multiple executions with mixed success
        for (int i = 0; i < 15; i++) {
            boolean success = i < 10; // 10 successes, 5 failures = 66.7% success rate
            errorMetrics.recordContributorPerformance(contributorClass, success, 10L);
        }
        
        // Then - Should not throw exception
        assertDoesNotThrow(() -> 
            errorMetrics.recordContributorPerformance(contributorClass, false, 10L)
        );
    }
    
    @Test
    void recordCauseChainDepth_ShouldTrackDepthDistribution() {
        // Given
        int depth = 3;
        boolean resolved = true;
        
        // When & Then - Should not throw exception
        assertDoesNotThrow(() -> 
            errorMetrics.recordCauseChainDepth(depth, resolved)
        );
    }
    
    @Test
    void recordCauseChainDepth_ShouldLogDeepChains() {
        // Given
        int deepDepth = 8; // Above 5 threshold
        boolean resolved = true;
        
        // When & Then - Should not throw exception
        assertDoesNotThrow(() -> 
            errorMetrics.recordCauseChainDepth(deepDepth, resolved)
        );
    }
}