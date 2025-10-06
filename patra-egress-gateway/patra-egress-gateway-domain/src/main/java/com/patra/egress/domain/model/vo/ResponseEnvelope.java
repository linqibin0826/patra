package com.patra.egress.domain.model.vo;

import java.util.Map;

/**
 * 响应封装值对象
 * 统一的响应语义结构
 * 
 * @param success 成功/失败标识（基于HTTP状态码）
 * @param statusCode HTTP状态码
 * @param headers 白名单过滤后的响应头
 * @param body 原始响应Body
 * @param bodyHash 响应Body的哈希值
 * @param rateLimitStatus 限流状态
 * @param retryAdvice 重试建议
 * @param snapshotMode 快照模式
 * @author linqibin
 * @since 0.1.0
 */
public record ResponseEnvelope(
    boolean success,
    int statusCode,
    Map<String, String> headers,
    String body,
    String bodyHash,
    RateLimitStatus rateLimitStatus,
    RetryAdvice retryAdvice,
    String snapshotMode
) {
    /**
     * 构造函数，确保不可变性
     */
    public ResponseEnvelope {
        // 创建不可变副本
        headers = headers != null ? Map.copyOf(headers) : Map.of();
    }
}
