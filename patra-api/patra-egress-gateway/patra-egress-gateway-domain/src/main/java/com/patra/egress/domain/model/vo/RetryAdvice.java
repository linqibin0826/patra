package com.patra.egress.domain.model.vo;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 重试建议值对象
 * 根据响应状态码和Retry-After头生成重试建议
 * 
 * @param retryable 是否可重试
 * @param suggestedDelay 建议延迟时间
 * @param reason 重试建议原因
 * @author linqibin
 * @since 0.1.0
 */
public record RetryAdvice(
    boolean retryable,
    Duration suggestedDelay,
    String reason
) {
    /**
     * 根据响应和配置生成重试建议
     * 
     * @param response HTTP响应
     * @param config 弹性配置
     * @return 重试建议
     */
    public static RetryAdvice fromResponse(HttpResponse response, ResilienceConfig config) {
        int statusCode = response.statusCode();
        
        // 429 Too Many Requests 或 503 Service Unavailable
        if (statusCode == 429 || statusCode == 503) {
            Duration delay = extractRetryAfter(response.headers())
                .orElse(config.retryBackoff());
            String reason = statusCode == 429 
                ? "Rate limited" 
                : "Service unavailable";
            return new RetryAdvice(true, delay, reason);
        }
        
        // 5xx 服务器错误（除了503已处理）
        if (statusCode >= 500) {
            return new RetryAdvice(true, config.retryBackoff(), "Server error");
        }
        
        // 408 Request Timeout
        if (statusCode == 408) {
            return new RetryAdvice(true, config.retryBackoff(), "Request timeout");
        }
        
        // 其他状态码不建议重试
        return new RetryAdvice(false, Duration.ZERO, "Not retryable");
    }
    
    /**
     * 从响应头中提取Retry-After值
     * HTTP响应头是大小写不敏感的
     *
     * @param headers 响应头
     * @return 延迟时间，如果没有Retry-After头则返回空
     */
    private static java.util.Optional<Duration> extractRetryAfter(Map<String, List<String>> headers) {
        if (headers == null || headers.isEmpty()) {
            return java.util.Optional.empty();
        }

        List<String> retryAfterValues = getHeaderIgnoreCase(headers, "Retry-After");
        if (retryAfterValues == null || retryAfterValues.isEmpty()) {
            return java.util.Optional.empty();
        }

        String retryAfter = retryAfterValues.get(0);

        try {
            // 尝试解析为秒数
            long seconds = Long.parseLong(retryAfter);
            return java.util.Optional.of(Duration.ofSeconds(seconds));
        } catch (NumberFormatException e) {
            // 如果不是数字，可能是HTTP日期格式，暂不支持
            return java.util.Optional.empty();
        }
    }

    /**
     * 忽略大小写获取响应头值
     * HTTP响应头名称是大小写不敏感的
     *
     * @param headers 响应头映射
     * @param headerName 响应头名称
     * @return 响应头值列表，如果不存在则返回null
     */
    private static List<String> getHeaderIgnoreCase(Map<String, List<String>> headers, String headerName) {
        // 先尝试精确匹配（性能优化）
        List<String> values = headers.get(headerName);
        if (values != null) {
            return values;
        }

        // 如果精确匹配失败，进行忽略大小写匹配
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(headerName)) {
                return entry.getValue();
            }
        }

        return null;
    }
}
