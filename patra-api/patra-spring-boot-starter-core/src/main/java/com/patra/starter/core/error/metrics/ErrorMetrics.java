package com.patra.starter.core.error.metrics;

import com.patra.common.error.codes.ErrorCodeLike;

/**
 * Interface for collecting error handling metrics.
 * Provides methods to track error resolution performance and distribution.
 * 
 * @author linqibin
 * @since 0.1.0
 */
public interface ErrorMetrics {
    
    /**
     * Records the time taken to resolve an exception to an error code.
     * 
     * @param exceptionClass the class of the exception that was resolved
     * @param errorCode the resolved error code
     * @param resolutionTimeMs the time taken to resolve in milliseconds
     * @param cacheHit whether the resolution was served from cache
     */
    void recordResolutionTime(Class<?> exceptionClass, ErrorCodeLike errorCode, 
                             long resolutionTimeMs, boolean cacheHit);
    
    /**
     * Records the distribution of error codes across the service.
     * 
     * @param errorCode the error code that was resolved
     * @param httpStatus the HTTP status code
     * @param serviceName the name of the service (from context prefix)
     */
    void recordErrorCodeDistribution(ErrorCodeLike errorCode, int httpStatus, String serviceName);
    
    /**
     * Records cache hit/miss statistics for error resolution.
     * 
     * @param exceptionClass the exception class
     * @param hit whether it was a cache hit or miss
     */
    void recordCacheHitMiss(Class<?> exceptionClass, boolean hit);
    
    /**
     * Records error mapping contributor performance.
     * 
     * @param contributorClass the class of the contributor
     * @param success whether the contributor succeeded
     * @param executionTimeMs the execution time in milliseconds
     */
    void recordContributorPerformance(Class<?> contributorClass, boolean success, long executionTimeMs);
    
    /**
     * Records cause chain traversal depth statistics.
     * 
     * @param depth the depth of cause chain traversal
     * @param resolved whether the exception was successfully resolved
     */
    void recordCauseChainDepth(int depth, boolean resolved);
}