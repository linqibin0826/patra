package com.patra.starter.core.error.metrics;

import com.patra.common.error.codes.ErrorCodeLike;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 错误指标默认实现，基于内存计数并配合结构化日志输出，提供基础可观测性。
 *
 * <p>可替换为更成熟的方案（如 Micrometer 等）。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
public class DefaultErrorMetrics implements ErrorMetrics {
    
    /** 按异常类型统计缓存命中/未命中次数 */
    private final ConcurrentHashMap<String, CacheStats> cacheStats = new ConcurrentHashMap<>();
    
    /** 错误码分布计数 */
    private final ConcurrentHashMap<String, LongAdder> errorCodeCounts = new ConcurrentHashMap<>();
    
    /** 贡献者性能统计 */
    private final ConcurrentHashMap<String, ContributorStats> contributorStats = new ConcurrentHashMap<>();
    
    /** 因果链深度统计 */
    private final ConcurrentHashMap<Integer, LongAdder> causeChainDepthCounts = new ConcurrentHashMap<>();
    
    @Override
    public void recordResolutionTime(Class<?> exceptionClass, ErrorCodeLike errorCode, 
                                   long resolutionTimeMs, boolean cacheHit) {
        
        // Structured logging for resolution performance
        log.info("error_resolution_performance exception_class={} error_code={} " +
                "resolution_time_ms={} cache_hit={}", 
                exceptionClass.getSimpleName(), errorCode.code(), resolutionTimeMs, cacheHit);
        
        // Track performance thresholds
        if (resolutionTimeMs > 100) {
            log.warn("slow_error_resolution exception_class={} error_code={} " +
                    "resolution_time_ms={} cache_hit={}", 
                    exceptionClass.getSimpleName(), errorCode.code(), resolutionTimeMs, cacheHit);
        }
    }
    
    @Override
    public void recordErrorCodeDistribution(ErrorCodeLike errorCode, int httpStatus, String serviceName) {
        String key = serviceName + ":" + errorCode.code();
        errorCodeCounts.computeIfAbsent(key, k -> new LongAdder()).increment();
        
        // Structured logging for error distribution
        log.info("error_code_distribution service={} error_code={} http_status={} count={}", 
                serviceName, errorCode.code(), httpStatus, 
                errorCodeCounts.get(key).sum());
        
        // Alert on high error rates for specific codes
        long count = errorCodeCounts.get(key).sum();
        if (count % 100 == 0) { // Log every 100 occurrences
            log.warn("high_error_frequency service={} error_code={} http_status={} count={}", 
                    serviceName, errorCode.code(), httpStatus, count);
        }
    }
    
    @Override
    public void recordCacheHitMiss(Class<?> exceptionClass, boolean hit) {
        String className = exceptionClass.getSimpleName();
        CacheStats stats = cacheStats.computeIfAbsent(className, k -> new CacheStats());
        
        if (hit) {
            stats.hits.increment();
        } else {
            stats.misses.increment();
        }
        
        // Periodic cache performance logging
        long totalRequests = stats.hits.sum() + stats.misses.sum();
        if (totalRequests % 50 == 0) { // Log every 50 requests
            double hitRate = (double) stats.hits.sum() / totalRequests;
            log.info("cache_performance exception_class={} hit_rate={} total_requests={} " +
                    "hits={} misses={}", 
                    className, String.format("%.2f", hitRate), totalRequests, 
                    stats.hits.sum(), stats.misses.sum());
        }
    }
    
    @Override
    public void recordContributorPerformance(Class<?> contributorClass, boolean success, long executionTimeMs) {
        String className = contributorClass.getSimpleName();
        ContributorStats stats = contributorStats.computeIfAbsent(className, k -> new ContributorStats());
        
        stats.totalExecutions.increment();
        if (success) {
            stats.successfulExecutions.increment();
        }
        
        // Structured logging for contributor performance
        log.debug("contributor_performance contributor={} success={} execution_time_ms={} " +
                 "success_rate={}", 
                 className, success, executionTimeMs, 
                 calculateSuccessRate(stats));
        
        // Alert on slow contributors
        if (executionTimeMs > 50) {
            log.warn("slow_contributor contributor={} execution_time_ms={} success={}", 
                    className, executionTimeMs, success);
        }
        
        // Alert on low success rates
        if (stats.totalExecutions.sum() >= 10) {
            double successRate = calculateSuccessRate(stats);
            if (successRate < 0.8) { // Less than 80% success rate
                log.warn("low_contributor_success_rate contributor={} success_rate={} " +
                        "total_executions={}", 
                        className, String.format("%.2f", successRate), stats.totalExecutions.sum());
            }
        }
    }
    
    @Override
    public void recordCauseChainDepth(int depth, boolean resolved) {
        causeChainDepthCounts.computeIfAbsent(depth, k -> new LongAdder()).increment();
        
        // Log deep cause chains as they may indicate issues
        if (depth > 5) {
            log.warn("deep_cause_chain depth={} resolved={}", depth, resolved);
        }
        
        // Periodic depth distribution logging
        long totalAtDepth = causeChainDepthCounts.get(depth).sum();
        if (totalAtDepth % 25 == 0) { // Log every 25 occurrences at this depth
            log.info("cause_chain_depth_distribution depth={} count={} resolved={}", 
                    depth, totalAtDepth, resolved);
        }
    }
    
    /**
     * 计算贡献者成功率。
     */
    private double calculateSuccessRate(ContributorStats stats) {
        long total = stats.totalExecutions.sum();
        if (total == 0) return 0.0;
        return (double) stats.successfulExecutions.sum() / total;
    }
    
    /**
     * 缓存统计结构。
     */
    private static class CacheStats {
        final LongAdder hits = new LongAdder();
        final LongAdder misses = new LongAdder();
    }
    
    /**
     * 贡献者统计结构。
     */
    private static class ContributorStats {
        final LongAdder totalExecutions = new LongAdder();
        final LongAdder successfulExecutions = new LongAdder();
    }
}
