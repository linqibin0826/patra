package com.patra.starter.feign.error.metrics;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Default implementation of FeignErrorMetrics that provides structured logging
 * and basic in-memory metrics collection for Feign error handling operations.
 * 
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
public class DefaultFeignErrorMetrics implements FeignErrorMetrics {
    
    /** Success/failure counters by method key */
    private final ConcurrentHashMap<String, DecodingStats> decodingStats = new ConcurrentHashMap<>();
    
    /** Trace ID extraction success counters */
    private final ConcurrentHashMap<String, LongAdder> traceIdSuccessCounts = new ConcurrentHashMap<>();
    
    /** Content type detection counters */
    private final ConcurrentHashMap<String, LongAdder> contentTypeStats = new ConcurrentHashMap<>();
    
    @Override
    public void recordProblemDetailParsing(String methodKey, int httpStatus, boolean success, long parseTimeMs) {
        // Structured logging for parsing performance
        log.info("feign_problem_detail_parsing method={} http_status={} success={} parse_time_ms={}", 
                methodKey, httpStatus, success, parseTimeMs);
        
        // Alert on slow parsing
        if (parseTimeMs > 100) {
            log.warn("slow_problem_detail_parsing method={} http_status={} parse_time_ms={}", 
                    methodKey, httpStatus, parseTimeMs);
        }
        
        // Alert on parsing failures
        if (!success) {
            log.warn("problem_detail_parsing_failed method={} http_status={}", methodKey, httpStatus);
        }
    }
    
    @Override
    public void recordErrorDecodingSuccess(String methodKey, int httpStatus, boolean decodingSuccess, boolean tolerantMode) {
        DecodingStats stats = decodingStats.computeIfAbsent(methodKey, k -> new DecodingStats());
        
        stats.totalAttempts.increment();
        if (decodingSuccess) {
            stats.successfulDecodings.increment();
        }
        if (tolerantMode) {
            stats.tolerantModeUsage.increment();
        }
        
        // Structured logging for decoding success
        log.info("feign_error_decoding method={} http_status={} success={} tolerant_mode={} " +
                "success_rate={}", 
                methodKey, httpStatus, decodingSuccess, tolerantMode, 
                calculateSuccessRate(stats));
        
        // Alert on low success rates
        if (stats.totalAttempts.sum() >= 10) {
            double successRate = calculateSuccessRate(stats);
            if (successRate < 0.8) { // Less than 80% success rate
                log.warn("low_feign_decoding_success_rate method={} success_rate={} total_attempts={}", 
                        methodKey, String.format("%.2f", successRate), stats.totalAttempts.sum());
            }
        }
        
        // Track tolerant mode usage
        if (tolerantMode && stats.totalAttempts.sum() % 10 == 0) {
            double tolerantModeRate = (double) stats.tolerantModeUsage.sum() / stats.totalAttempts.sum();
            log.info("feign_tolerant_mode_usage method={} tolerant_mode_rate={} total_attempts={}", 
                    methodKey, String.format("%.2f", tolerantModeRate), stats.totalAttempts.sum());
        }
    }
    
    @Override
    public void recordTraceIdExtraction(String methodKey, boolean traceIdFound, String headerUsed) {
        String key = methodKey + ":" + (traceIdFound ? "found" : "not_found");
        traceIdSuccessCounts.computeIfAbsent(key, k -> new LongAdder()).increment();
        
        // Structured logging for trace ID extraction
        if (traceIdFound) {
            log.debug("feign_trace_id_extracted method={} header_used={}", methodKey, headerUsed);
        } else {
            log.debug("feign_trace_id_not_found method={}", methodKey);
        }
        
        // Periodic trace ID extraction rate logging
        long foundCount = traceIdSuccessCounts.getOrDefault(methodKey + ":found", new LongAdder()).sum();
        long notFoundCount = traceIdSuccessCounts.getOrDefault(methodKey + ":not_found", new LongAdder()).sum();
        long totalAttempts = foundCount + notFoundCount;
        
        if (totalAttempts % 25 == 0 && totalAttempts > 0) { // Log every 25 attempts
            double extractionRate = (double) foundCount / totalAttempts;
            log.info("feign_trace_id_extraction_rate method={} extraction_rate={} total_attempts={}", 
                    methodKey, String.format("%.2f", extractionRate), totalAttempts);
        }
    }
    
    @Override
    public void recordResponseBodyReading(String methodKey, int bodySize, long readTimeMs, boolean truncated) {
        // Structured logging for response body reading
        log.debug("feign_response_body_reading method={} body_size_bytes={} read_time_ms={} truncated={}", 
                 methodKey, bodySize, readTimeMs, truncated);
        
        // Alert on large response bodies
        if (bodySize > 10240) { // 10KB
            log.warn("large_feign_response_body method={} body_size_bytes={} truncated={}", 
                    methodKey, bodySize, truncated);
        }
        
        // Alert on slow body reading
        if (readTimeMs > 50) {
            log.warn("slow_feign_response_body_reading method={} read_time_ms={} body_size_bytes={}", 
                    methodKey, readTimeMs, bodySize);
        }
        
        // Alert on truncated responses
        if (truncated) {
            log.warn("feign_response_body_truncated method={} body_size_bytes={}", methodKey, bodySize);
        }
    }
    
    @Override
    public void recordContentTypeDetection(String methodKey, String contentType, boolean isProblemDetail) {
        String key = isProblemDetail ? "problem_detail" : "other";
        contentTypeStats.computeIfAbsent(key, k -> new LongAdder()).increment();
        
        // Structured logging for content type detection
        log.debug("feign_content_type_detection method={} content_type={} is_problem_detail={}", 
                 methodKey, contentType, isProblemDetail);
        
        // Periodic content type distribution logging
        long problemDetailCount = contentTypeStats.getOrDefault("problem_detail", new LongAdder()).sum();
        long otherCount = contentTypeStats.getOrDefault("other", new LongAdder()).sum();
        long totalResponses = problemDetailCount + otherCount;
        
        if (totalResponses % 50 == 0 && totalResponses > 0) { // Log every 50 responses
            double problemDetailRate = (double) problemDetailCount / totalResponses;
            log.info("feign_content_type_distribution problem_detail_rate={} total_responses={} " +
                    "problem_detail_count={} other_count={}", 
                    String.format("%.2f", problemDetailRate), totalResponses, 
                    problemDetailCount, otherCount);
        }
    }
    
    /**
     * Calculates success rate for decoding operations.
     */
    private double calculateSuccessRate(DecodingStats stats) {
        long total = stats.totalAttempts.sum();
        if (total == 0) return 0.0;
        return (double) stats.successfulDecodings.sum() / total;
    }
    
    /**
     * Decoding statistics holder.
     */
    private static class DecodingStats {
        final LongAdder totalAttempts = new LongAdder();
        final LongAdder successfulDecodings = new LongAdder();
        final LongAdder tolerantModeUsage = new LongAdder();
    }
}