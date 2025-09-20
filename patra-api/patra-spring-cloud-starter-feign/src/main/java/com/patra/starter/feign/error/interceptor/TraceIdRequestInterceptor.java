package com.patra.starter.feign.error.interceptor;

import com.patra.starter.core.error.config.TracingProperties;
import com.patra.starter.core.error.spi.TraceProvider;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * 将 TraceId 透传到下游请求的 Feign 拦截器。
 *
 * <p>通过可配置的请求头名以及 {@link com.patra.starter.core.error.spi.TraceProvider} SPI，
 * 兼容多种链路追踪系统。拦截器会尝试从当前执行上下文中提取 TraceId，
 * 并使用 {@link com.patra.starter.core.error.config.TracingProperties} 配置的第一个请求头名写入请求，
 * 以确保跨服务的调用链路关联。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
public class TraceIdRequestInterceptor implements RequestInterceptor {
    
    private final TraceProvider traceProvider;
    private final TracingProperties tracingProperties;
    
    /**
     * 构造函数。
     *
     * @param traceProvider 提供当前 TraceId 的抽象
     * @param tracingProperties 链路追踪配置（含请求头名）
     */
    public TraceIdRequestInterceptor(TraceProvider traceProvider, TracingProperties tracingProperties) {
        this.traceProvider = traceProvider;
        this.tracingProperties = tracingProperties;
    }
    
    /**
     * 对即将发出的请求写入 TraceId。
     *
     * @param template Feign 请求模板
     */
    @Override
    public void apply(RequestTemplate template) {
        try {
            Optional<String> traceId = traceProvider.getCurrentTraceId();
            
            if (traceId.isPresent()) {
                String headerName = getTraceHeaderName();
                template.header(headerName, traceId.get());
                
                log.debug("Added TraceId to request: {}={} url={}", headerName, traceId.get(), template.url());
            } else {
                log.debug("No TraceId available, url={}", template.url());
            }
            
        } catch (Exception e) {
            // Do not fail request if trace propagation fails
            log.warn("Failed to propagate TraceId, url={}, error={}", template.url(), e.getMessage());
        }
    }
    
    /**
     * 获取写入 TraceId 所使用的请求头名（取配置的第一个）。
     *
     * @return TraceId 请求头名
     */
    private String getTraceHeaderName() {
        if (tracingProperties.getHeaderNames() != null && !tracingProperties.getHeaderNames().isEmpty()) {
            return tracingProperties.getHeaderNames().get(0);
        }
        
        // 无配置时的默认兜底
        return "traceId";
    }
}
