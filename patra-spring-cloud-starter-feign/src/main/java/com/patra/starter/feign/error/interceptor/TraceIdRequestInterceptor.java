package com.patra.starter.feign.error.interceptor;

import com.patra.starter.core.error.config.TracingProperties;
import com.patra.starter.core.error.spi.TraceProvider;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * Feign interceptor that propagates the current trace identifier downstream.
 *
 * <p>Uses the configurable header names from {@link TracingProperties} together with the
 * {@link TraceProvider} SPI so it can adapt to different tracing systems. The interceptor reads the
 * current trace ID from the execution context and writes it to the first configured header.</p>
 */
@Slf4j
public class TraceIdRequestInterceptor implements RequestInterceptor {
    
    private final TraceProvider traceProvider;
    private final TracingProperties tracingProperties;
    
    public TraceIdRequestInterceptor(TraceProvider traceProvider, TracingProperties tracingProperties) {
        this.traceProvider = traceProvider;
        this.tracingProperties = tracingProperties;
    }

    /**
     * Inject the trace identifier into the outbound request headers.
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
    
    /** @return The header name used for trace propagation (first configured value or default). */
    private String getTraceHeaderName() {
        if (tracingProperties.getHeaderNames() != null && !tracingProperties.getHeaderNames().isEmpty()) {
            return tracingProperties.getHeaderNames().get(0);
        }
        
        // Fall back to a conventional header name when nothing is configured.
        return "traceId";
    }
}
