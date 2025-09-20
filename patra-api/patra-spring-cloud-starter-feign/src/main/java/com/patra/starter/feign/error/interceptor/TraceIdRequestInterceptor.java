package com.patra.starter.feign.error.interceptor;

import com.patra.starter.core.error.config.TracingProperties;
import com.patra.starter.core.error.spi.TraceProvider;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * Feign request interceptor that automatically propagates trace IDs to outgoing requests.
 * Supports multiple tracing systems by using configurable header names and TraceProvider SPI.
 * 
 * The interceptor attempts to extract the current trace ID from the execution context
 * and adds it to the outgoing request using the first configured header name.
 * This ensures distributed tracing correlation across service boundaries.
 * 
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
public class TraceIdRequestInterceptor implements RequestInterceptor {
    
    private final TraceProvider traceProvider;
    private final TracingProperties tracingProperties;
    
    /**
     * Constructs a new TraceIdRequestInterceptor with the specified dependencies.
     * 
     * @param traceProvider the trace provider for extracting current trace ID
     * @param tracingProperties the tracing configuration including header names
     */
    public TraceIdRequestInterceptor(TraceProvider traceProvider, TracingProperties tracingProperties) {
        this.traceProvider = traceProvider;
        this.tracingProperties = tracingProperties;
    }
    
    /**
     * Applies trace ID propagation to the outgoing request.
     * 
     * @param template the Feign request template to modify
     */
    @Override
    public void apply(RequestTemplate template) {
        try {
            Optional<String> traceId = traceProvider.getCurrentTraceId();
            
            if (traceId.isPresent()) {
                String headerName = getTraceHeaderName();
                template.header(headerName, traceId.get());
                
                log.debug("Added trace ID to outgoing request: {}={} for URL: {}", 
                         headerName, traceId.get(), template.url());
            } else {
                log.debug("No trace ID available for outgoing request to: {}", template.url());
            }
            
        } catch (Exception e) {
            // Don't fail the request if trace propagation fails
            log.warn("Failed to propagate trace ID for request to {}: {}", 
                    template.url(), e.getMessage());
        }
    }
    
    /**
     * Gets the primary trace header name to use for outgoing requests.
     * Uses the first configured header name from TracingProperties.
     * 
     * @return the trace header name to use
     */
    private String getTraceHeaderName() {
        if (tracingProperties.getHeaderNames() != null && !tracingProperties.getHeaderNames().isEmpty()) {
            return tracingProperties.getHeaderNames().get(0);
        }
        
        // Fallback to default if no headers configured
        return "traceId";
    }
}