package com.patra.egress.domain.model.vo;

import java.util.List;
import java.util.Map;

/**
 * 外部服务限流信息值对象
 * 从外部服务响应头中提取限流信息
 * 
 * @param limit 限流上限（X-RateLimit-Limit）
 * @param remaining 剩余配额（X-RateLimit-Remaining）
 * @param resetTimestamp 重置时间戳（X-RateLimit-Reset）
 * @author linqibin
 * @since 0.1.0
 */
public record ExternalRateLimitInfo(
    Integer limit,
    Integer remaining,
    Long resetTimestamp
) {
    /**
     * 从响应头提取限流信息
     * HTTP响应头是大小写不敏感的，需要进行忽略大小写的匹配
     *
     * @param headers 响应头
     * @return 外部限流信息，如果响应头中没有限流信息则返回null
     */
    public static ExternalRateLimitInfo fromHeaders(Map<String, List<String>> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }

        Integer limit = extractIntHeader(headers, "X-RateLimit-Limit");
        Integer remaining = extractIntHeader(headers, "X-RateLimit-Remaining");
        Long resetTimestamp = extractLongHeader(headers, "X-RateLimit-Reset");

        // 如果所有字段都为null，返回null
        if (limit == null && remaining == null && resetTimestamp == null) {
            return null;
        }

        return new ExternalRateLimitInfo(limit, remaining, resetTimestamp);
    }

    /**
     * 从响应头中提取整数值（忽略大小写）
     *
     * @param headers 响应头映射
     * @param headerName 响应头名称
     * @return 提取的整数值，如果不存在或解析失败则返回null
     */
    private static Integer extractIntHeader(Map<String, List<String>> headers, String headerName) {
        List<String> values = getHeaderIgnoreCase(headers, headerName);
        if (values == null || values.isEmpty()) {
            return null;
        }

        try {
            return Integer.parseInt(values.get(0));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 从响应头中提取长整数值（忽略大小写）
     *
     * @param headers 响应头映射
     * @param headerName 响应头名称
     * @return 提取的长整数值，如果不存在或解析失败则返回null
     */
    private static Long extractLongHeader(Map<String, List<String>> headers, String headerName) {
        List<String> values = getHeaderIgnoreCase(headers, headerName);
        if (values == null || values.isEmpty()) {
            return null;
        }

        try {
            return Long.parseLong(values.get(0));
        } catch (NumberFormatException e) {
            return null;
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
