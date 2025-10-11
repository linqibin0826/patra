package com.patra.starter.core.error.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuration properties for distributed tracing support.
 */
@Data
@ConfigurationProperties(prefix = "patra.tracing")
public class TracingProperties {

    /** Header names (in priority order) used to resolve the trace identifier. */
    private List<String> headerNames = List.of("traceId", "X-B3-TraceId", "traceparent");
}
