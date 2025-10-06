package com.patra.egress.domain.model.vo;

import java.util.List;
import java.util.Map;

/**
 * HTTP响应值对象
 * 
 * @param statusCode HTTP状态码
 * @param headers 响应头
 * @param body 响应体
 * @author linqibin
 * @since 0.1.0
 */
public record HttpResponse(
    int statusCode,
    Map<String, List<String>> headers,
    String body
) {
    /**
     * 构造函数，确保不可变性
     */
    public HttpResponse {
        // 创建不可变副本
        headers = headers != null ? Map.copyOf(headers) : Map.of();
    }
    
    /**
     * 判断响应是否成功（2xx状态码）
     * 
     * @return true表示成功，false表示失败
     */
    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }
}
