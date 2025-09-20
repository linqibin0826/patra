package com.patra.starter.core.error.trace;

import com.patra.starter.core.error.config.TracingProperties;
import com.patra.starter.core.error.spi.TraceProvider;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.Optional;

/**
 * 基于 MDC（映射诊断上下文）从已配置的请求头名提取 TraceId 的实现。
 *
 * <p>兼容多种分布式追踪常见的头格式。
 *
 * @author linqibin
 * @since 0.1.0
 * @see com.patra.starter.core.error.config.TracingProperties
 */
@Slf4j
public class HeaderBasedTraceProvider implements TraceProvider {
    
    /** 链路追踪配置 */
    private final TracingProperties tracingProperties;
    
    /**
     * 构造函数。
     *
     * @param tracingProperties 追踪配置，不能为空
     */
    public HeaderBasedTraceProvider(TracingProperties tracingProperties) {
        this.tracingProperties = tracingProperties;
    }
    
    @Override
    public Optional<String> getCurrentTraceId() {
        // Try configured header names in order
        for (String headerName : tracingProperties.getHeaderNames()) {
            String traceId = MDC.get(headerName);
            if (traceId != null && !traceId.trim().isEmpty()) {
                log.debug("Found trace ID '{}' from MDC key '{}'", traceId, headerName);
                return Optional.of(traceId.trim());
            }
        }
        
        log.debug("No trace ID found in MDC for any configured header names: {}", 
                 tracingProperties.getHeaderNames());
        return Optional.empty();
    }
}
