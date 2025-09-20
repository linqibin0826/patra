package com.patra.starter.core.error.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 分布式链路追踪配置项。
 *
 * <p>用于配置从哪些请求头提取 TraceId。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@ConfigurationProperties(prefix = "patra.tracing")
public class TracingProperties {
    
    /** 用于读取 TraceId 的请求头名列表（按优先级顺序） */
    private List<String> headerNames = List.of("traceId", "X-B3-TraceId", "traceparent");
}
