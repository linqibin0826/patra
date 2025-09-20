package com.patra.starter.core.error.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Tracing configuration properties for distributed tracing integration.
 * Configures trace ID extraction from various headers.
 * 
 * @author linqibin
 * @since 0.1.0
 */
@Data
@ConfigurationProperties(prefix = "patra.tracing")
public class TracingProperties {
    
    /** List of header names to check for trace ID */
    private List<String> headerNames = List.of("traceId", "X-B3-TraceId", "traceparent");
}