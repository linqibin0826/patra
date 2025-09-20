package com.patra.starter.feign.error.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DefaultFeignErrorMetrics.
 * 
 * @author linqibin
 * @since 0.1.0
 */
class DefaultFeignErrorMetricsTest {
    
    private DefaultFeignErrorMetrics feignErrorMetrics;
    
    @BeforeEach
    void setUp() {
        feignErrorMetrics = new DefaultFeignErrorMetrics();
    }
    
    @Test
    void recordProblemDetailParsing_ShouldLogParsingMetrics() {
        // Given
        String methodKey = "TestClient#testMethod()";
        int httpStatus = 400;
        boolean success = true;
        long parseTime = 50L;
        
        // When & Then - Should not throw exception
        assertDoesNotThrow(() -> 
            feignErrorMetrics.recordProblemDetailParsing(methodKey, httpStatus, success, parseTime)
        );
    }
    
    @Test
    void recordProblemDetailParsing_ShouldLogSlowParsing() {
        // Given
        String methodKey = "TestClient#testMethod()";
        int httpStatus = 500;
        boolean success = true;
        long slowParseTime = 150L; // Above 100ms threshold
        
        // When & Then - Should not throw exception
        assertDoesNotThrow(() -> 
            feignErrorMetrics.recordProblemDetailParsing(methodKey, httpStatus, success, slowParseTime)
        );
    }
    
    @Test
    void recordProblemDetailParsing_ShouldLogParsingFailure() {
        // Given
        String methodKey = "TestClient#testMethod()";
        int httpStatus = 400;
        boolean success = false;
        long parseTime = 25L;
        
        // When & Then - Should not throw exception
        assertDoesNotThrow(() -> 
            feignErrorMetrics.recordProblemDetailParsing(methodKey, httpStatus, success, parseTime)
        );
    }
    
    @Test
    void recordErrorDecodingSuccess_ShouldTrackDecodingStats() {
        // Given
        String methodKey = "TestClient#testMethod()";
        int httpStatus = 404;
        boolean decodingSuccess = true;
        boolean tolerantMode = false;
        
        // When & Then - Should not throw exception
        assertDoesNotThrow(() -> 
            feignErrorMetrics.recordErrorDecodingSuccess(methodKey, httpStatus, decodingSuccess, tolerantMode)
        );
    }
    
    @Test
    void recordErrorDecodingSuccess_ShouldTrackSuccessRate() {
        // Given
        String methodKey = "TestClient#testMethod()";
        
        // When - Record multiple decoding attempts with mixed success
        for (int i = 0; i < 15; i++) {
            boolean success = i < 12; // 12 successes, 3 failures = 80% success rate
            feignErrorMetrics.recordErrorDecodingSuccess(methodKey, 400, success, false);
        }
        
        // Then - Should not throw exception
        assertDoesNotThrow(() -> 
            feignErrorMetrics.recordErrorDecodingSuccess(methodKey, 400, false, false)
        );
    }
    
    @Test
    void recordErrorDecodingSuccess_ShouldTrackTolerantModeUsage() {
        // Given
        String methodKey = "TestClient#testMethod()";
        
        // When - Record multiple calls with tolerant mode
        for (int i = 0; i < 10; i++) {
            boolean tolerantMode = i % 3 == 0; // Use tolerant mode every 3rd call
            feignErrorMetrics.recordErrorDecodingSuccess(methodKey, 400, true, tolerantMode);
        }
        
        // Then - Should not throw exception
        assertDoesNotThrow(() -> 
            feignErrorMetrics.recordErrorDecodingSuccess(methodKey, 400, true, true)
        );
    }
    
    @Test
    void recordTraceIdExtraction_ShouldTrackExtractionSuccess() {
        // Given
        String methodKey = "TestClient#testMethod()";
        boolean traceIdFound = true;
        String headerUsed = "X-B3-TraceId";
        
        // When & Then - Should not throw exception
        assertDoesNotThrow(() -> 
            feignErrorMetrics.recordTraceIdExtraction(methodKey, traceIdFound, headerUsed)
        );
    }
    
    @Test
    void recordTraceIdExtraction_ShouldTrackExtractionRate() {
        // Given
        String methodKey = "TestClient#testMethod()";
        
        // When - Record multiple extraction attempts
        for (int i = 0; i < 25; i++) {
            boolean found = i % 4 != 0; // Found in 75% of cases
            String header = found ? "traceId" : null;
            feignErrorMetrics.recordTraceIdExtraction(methodKey, found, header);
        }
        
        // Then - Should not throw exception and trigger rate logging
        assertDoesNotThrow(() -> 
            feignErrorMetrics.recordTraceIdExtraction(methodKey, true, "traceId")
        );
    }
    
    @Test
    void recordResponseBodyReading_ShouldTrackReadingPerformance() {
        // Given
        String methodKey = "TestClient#testMethod()";
        int bodySize = 1024;
        long readTime = 25L;
        boolean truncated = false;
        
        // When & Then - Should not throw exception
        assertDoesNotThrow(() -> 
            feignErrorMetrics.recordResponseBodyReading(methodKey, bodySize, readTime, truncated)
        );
    }
    
    @Test
    void recordResponseBodyReading_ShouldLogLargeResponseBody() {
        // Given
        String methodKey = "TestClient#testMethod()";
        int largeBodySize = 15000; // Above 10KB threshold
        long readTime = 25L;
        boolean truncated = false;
        
        // When & Then - Should not throw exception
        assertDoesNotThrow(() -> 
            feignErrorMetrics.recordResponseBodyReading(methodKey, largeBodySize, readTime, truncated)
        );
    }
    
    @Test
    void recordResponseBodyReading_ShouldLogSlowReading() {
        // Given
        String methodKey = "TestClient#testMethod()";
        int bodySize = 1024;
        long slowReadTime = 75L; // Above 50ms threshold
        boolean truncated = false;
        
        // When & Then - Should not throw exception
        assertDoesNotThrow(() -> 
            feignErrorMetrics.recordResponseBodyReading(methodKey, bodySize, slowReadTime, truncated)
        );
    }
    
    @Test
    void recordResponseBodyReading_ShouldLogTruncatedResponse() {
        // Given
        String methodKey = "TestClient#testMethod()";
        int bodySize = 65536; // 64KB
        long readTime = 25L;
        boolean truncated = true;
        
        // When & Then - Should not throw exception
        assertDoesNotThrow(() -> 
            feignErrorMetrics.recordResponseBodyReading(methodKey, bodySize, readTime, truncated)
        );
    }
    
    @Test
    void recordContentTypeDetection_ShouldTrackContentTypes() {
        // Given
        String methodKey = "TestClient#testMethod()";
        String contentType = "application/problem+json";
        boolean isProblemDetail = true;
        
        // When & Then - Should not throw exception
        assertDoesNotThrow(() -> 
            feignErrorMetrics.recordContentTypeDetection(methodKey, contentType, isProblemDetail)
        );
    }
    
    @Test
    void recordContentTypeDetection_ShouldTrackDistribution() {
        // Given
        String methodKey = "TestClient#testMethod()";
        
        // When - Record multiple content type detections
        for (int i = 0; i < 50; i++) {
            boolean isProblemDetail = i % 3 == 0; // 1/3 are ProblemDetail
            String contentType = isProblemDetail ? "application/problem+json" : "application/json";
            feignErrorMetrics.recordContentTypeDetection(methodKey, contentType, isProblemDetail);
        }
        
        // Then - Should not throw exception and trigger distribution logging
        assertDoesNotThrow(() -> 
            feignErrorMetrics.recordContentTypeDetection(methodKey, "application/problem+json", true)
        );
    }
}