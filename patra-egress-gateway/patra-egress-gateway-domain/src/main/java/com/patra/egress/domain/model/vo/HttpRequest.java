package com.patra.egress.domain.model.vo;

import java.util.Map;

/**
 * HTTP请求值对象
 *
 * @param url 目标URL
 * @param method HTTP方法
 * @param headers 请求头
 * @param body 请求体
 * @author linqibin
 * @since 0.1.0
 */
public record HttpRequest(
    String url,
    HttpMethod method,
    Map<String, String> headers,
    String body
) {
    /**
     * 构造函数，确保不可变性
     */
    public HttpRequest {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL cannot be null or blank");
        }
        if (method == null) {
            throw new IllegalArgumentException("HTTP method cannot be null");
        }
        // 创建不可变副本
        headers = headers != null ? Map.copyOf(headers) : Map.of();
    }
}
