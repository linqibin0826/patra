package com.patra.starter.core.error.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Error handling configuration properties for the core starter.
 * Provides configuration for error handling behavior and status mapping.
 * 
 * @author linqibin
 * @since 0.1.0
 */
@Data
@ConfigurationProperties(prefix = "patra.error")
public class ErrorProperties {
    
    /** Whether error handling is enabled */
    private boolean enabled = true;
    
    /** Context prefix for error codes (e.g., REG, ORD, INV) - REQUIRED */
    private String contextPrefix;
    
    /** Status mapping configuration */
    private MapStatusProperties mapStatus = new MapStatusProperties();
    
    /** Monitoring and observability configuration */
    private MonitoringProperties monitoring = new MonitoringProperties();
    
    /**
     * Status mapping configuration properties.
     */
    @Data
    public static class MapStatusProperties {
        /** Status mapping strategy name */
        private String strategy = "suffix-heuristic";
    }
    
    /**
     * Monitoring and observability configuration properties.
     */
    @Data
    public static class MonitoringProperties {
        /** Whether monitoring is enabled */
        private boolean enabled = true;
        
        /** Circuit breaker configuration */
        private CircuitBreakerProperties circuitBreaker = new CircuitBreakerProperties();
        
        /** Metrics collection configuration */
        private MetricsProperties metrics = new MetricsProperties();
    }
    
    /**
     * Circuit breaker configuration properties.
     */
    @Data
    public static class CircuitBreakerProperties {
        /** Whether circuit breakers are enabled for error mapping contributors */
        private boolean enabled = true;
        
        /** Number of consecutive failures to open the circuit */
        private int failureThreshold = 5;
        
        /** Failure rate threshold (0.0 to 1.0) to open the circuit */
        private double failureRateThreshold = 0.5;
        
        /** Timeout duration in milliseconds before attempting to close the circuit */
        private long timeoutMs = 60000; // 1 minute
        
        /** Size of the sliding window for tracking calls */
        private int slidingWindowSize = 100;
    }
    
    /**
     * Metrics collection configuration properties.
     */
    @Data
    public static class MetricsProperties {
        /** Whether metrics collection is enabled */
        private boolean enabled = true;
        
        /** Whether to log slow resolution performance */
        private boolean logSlowResolution = true;
        
        /** Threshold in milliseconds for slow resolution logging */
        private long slowResolutionThresholdMs = 100;
        
        /** Whether to log cache performance statistics */
        private boolean logCachePerformance = true;
        
        /** Interval for cache performance logging (number of requests) */
        private int cachePerformanceLogInterval = 50;
    }
}