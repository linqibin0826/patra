package com.patra.starter.feign.error.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Feign error handling.
 * Provides configuration for error decoding behavior, tolerant mode,
 * and ProblemDetail response handling.
 * 
 * @author linqibin
 * @since 0.1.0
 */
@Data
@ConfigurationProperties(prefix = "patra.feign.problem")
public class FeignErrorProperties {
    
    /** Whether Feign error handling is enabled */
    private boolean enabled = true;
    
    /** 
     * Whether to use tolerant mode for error decoding.
     * In tolerant mode, the decoder gracefully handles:
     * - 404 responses with empty bodies
     * - Non-JSON responses
     * - Malformed ProblemDetail responses
     * When false, strict mode throws FeignException for non-ProblemDetail responses.
     */
    private boolean tolerant = true;
    
    /** Maximum size of error response body to read (in bytes) */
    private int maxErrorBodySize = 64 * 1024; // 64KB
    
    /** Whether to include stack traces in error responses (for debugging) */
    private boolean includeStackTrace = false;
    
    /** Monitoring and observability configuration */
    private MonitoringProperties monitoring = new MonitoringProperties();
    
    /**
     * Monitoring configuration properties for Feign error handling.
     */
    @Data
    public static class MonitoringProperties {
        /** Whether monitoring is enabled */
        private boolean enabled = true;
        
        /** Whether to log slow parsing operations */
        private boolean logSlowParsing = true;
        
        /** Threshold in milliseconds for slow parsing logging */
        private long slowParsingThresholdMs = 100;
        
        /** Whether to log response body reading performance */
        private boolean logResponseBodyReading = true;
        
        /** Threshold in milliseconds for slow response body reading */
        private long slowBodyReadingThresholdMs = 50;
        
        /** Interval for decoding success rate logging (number of attempts) */
        private int decodingSuccessLogInterval = 10;
        
        /** Interval for trace ID extraction rate logging (number of attempts) */
        private int traceIdExtractionLogInterval = 25;
        
        /** Interval for content type distribution logging (number of responses) */
        private int contentTypeDistributionLogInterval = 50;
    }
}