package com.patra.starter.feign.error.metrics;

/**
 * Interface for collecting Feign error handling metrics.
 * Provides methods to track Feign error decoding performance and success rates.
 * 
 * @author linqibin
 * @since 0.1.0
 */
public interface FeignErrorMetrics {
    
    /**
     * Records the success or failure of ProblemDetail parsing.
     * 
     * @param methodKey the Feign method key
     * @param httpStatus the HTTP status code
     * @param success whether parsing was successful
     * @param parseTimeMs the time taken to parse in milliseconds
     */
    void recordProblemDetailParsing(String methodKey, int httpStatus, boolean success, long parseTimeMs);
    
    /**
     * Records the overall error decoding success rate.
     * 
     * @param methodKey the Feign method key
     * @param httpStatus the HTTP status code
     * @param decodingSuccess whether decoding was successful
     * @param tolerantMode whether tolerant mode was used
     */
    void recordErrorDecodingSuccess(String methodKey, int httpStatus, boolean decodingSuccess, boolean tolerantMode);
    
    /**
     * Records trace ID extraction success from response headers.
     * 
     * @param methodKey the Feign method key
     * @param traceIdFound whether trace ID was found
     * @param headerUsed the header that contained the trace ID (if found)
     */
    void recordTraceIdExtraction(String methodKey, boolean traceIdFound, String headerUsed);
    
    /**
     * Records response body reading performance and issues.
     * 
     * @param methodKey the Feign method key
     * @param bodySize the size of the response body in bytes
     * @param readTimeMs the time taken to read the body
     * @param truncated whether the body was truncated due to size limits
     */
    void recordResponseBodyReading(String methodKey, int bodySize, long readTimeMs, boolean truncated);
    
    /**
     * Records content type detection for ProblemDetail responses.
     * 
     * @param methodKey the Feign method key
     * @param contentType the detected content type
     * @param isProblemDetail whether it was identified as ProblemDetail
     */
    void recordContentTypeDetection(String methodKey, String contentType, boolean isProblemDetail);
}