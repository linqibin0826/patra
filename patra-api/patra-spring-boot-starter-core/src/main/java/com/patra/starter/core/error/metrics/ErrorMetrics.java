package com.patra.starter.core.error.metrics;

import com.patra.common.error.codes.ErrorCodeLike;

/**
 * 错误处理指标采集接口。
 *
 * <p>用于记录错误解析性能、错误码分布、缓存命中、贡献者表现以及因果链深度等信息。
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface ErrorMetrics {
    
    /**
     * 记录将异常解析为错误码所花费的时间。
     *
     * @param exceptionClass 被解析的异常类型
     * @param errorCode 解析得到的错误码
     * @param resolutionTimeMs 解析耗时（毫秒）
     * @param cacheHit 是否命中缓存
     */
    void recordResolutionTime(Class<?> exceptionClass, ErrorCodeLike errorCode, 
                             long resolutionTimeMs, boolean cacheHit);
    
    /**
     * 记录服务内错误码分布。
     *
     * @param errorCode 错误码
     * @param httpStatus 对应的 HTTP 状态码
     * @param serviceName 服务名（来自上下文前缀）
     */
    void recordErrorCodeDistribution(ErrorCodeLike errorCode, int httpStatus, String serviceName);
    
    /**
     * 记录错误解析的缓存命中/未命中统计。
     *
     * @param exceptionClass 异常类型
     * @param hit 是否命中
     */
    void recordCacheHitMiss(Class<?> exceptionClass, boolean hit);
    
    /**
     * 记录错误映射贡献者的性能表现。
     *
     * @param contributorClass 贡献者类型
     * @param success 是否成功
     * @param executionTimeMs 执行耗时（毫秒）
     */
    void recordContributorPerformance(Class<?> contributorClass, boolean success, long executionTimeMs);
    
    /**
     * 记录因果链回溯深度统计。
     *
     * @param depth 回溯深度
     * @param resolved 是否最终解析成功
     */
    void recordCauseChainDepth(int depth, boolean resolved);
}
