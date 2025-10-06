package com.patra.egress.domain.model.vo;

import java.time.Duration;
import java.util.List;

/**
 * 弹性配置值对象
 * 
 * @param timeout 超时时间
 * @param maxRetries 最大重试次数
 * @param retryBackoff 重试退避时间
 * @param rateLimit 限流速率（每秒请求数）
 * @param circuitBreakerThreshold 熔断阈值（失败次数）
 * @param circuitBreakerWindow 熔断时间窗口
 * @param responseHeaderWhitelist 响应头白名单
 * @author linqibin
 * @since 0.1.0
 */
public record ResilienceConfig(
    Duration timeout,
    int maxRetries,
    Duration retryBackoff,
    int rateLimit,
    int circuitBreakerThreshold,
    Duration circuitBreakerWindow,
    List<String> responseHeaderWhitelist
) {
    /**
     * 构造函数，确保不可变性
     */
    public ResilienceConfig {
        // 创建不可变副本
        responseHeaderWhitelist = responseHeaderWhitelist != null 
            ? List.copyOf(responseHeaderWhitelist) 
            : List.of();
    }
    
    /**
     * 校验配置有效性
     * 
     * @throws IllegalArgumentException 如果配置无效
     */
    public void validate() {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("MaxRetries cannot be negative");
        }
        if (retryBackoff == null || retryBackoff.isNegative()) {
            throw new IllegalArgumentException("RetryBackoff cannot be negative");
        }
        if (rateLimit <= 0) {
            throw new IllegalArgumentException("RateLimit must be positive");
        }
        if (circuitBreakerThreshold <= 0) {
            throw new IllegalArgumentException("CircuitBreakerThreshold must be positive");
        }
        if (circuitBreakerWindow == null || circuitBreakerWindow.isNegative() || circuitBreakerWindow.isZero()) {
            throw new IllegalArgumentException("CircuitBreakerWindow must be positive");
        }
    }
    
    /**
     * 合并配置（不超过最大值）
     * 
     * @param max 最大配置限制
     * @return 合并后的配置
     */
    public ResilienceConfig mergeWithMax(ResilienceConfig max) {
        return new ResilienceConfig(
            timeout.compareTo(max.timeout) > 0 ? max.timeout : timeout,
            Math.min(maxRetries, max.maxRetries),
            retryBackoff.compareTo(max.retryBackoff) > 0 ? max.retryBackoff : retryBackoff,
            Math.min(rateLimit, max.rateLimit),
            Math.min(circuitBreakerThreshold, max.circuitBreakerThreshold),
            circuitBreakerWindow.compareTo(max.circuitBreakerWindow) > 0 
                ? max.circuitBreakerWindow 
                : circuitBreakerWindow,
            responseHeaderWhitelist != null && !responseHeaderWhitelist.isEmpty()
                ? responseHeaderWhitelist 
                : max.responseHeaderWhitelist
        );
    }
}
