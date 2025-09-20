package com.patra.starter.core.error.trace;

import com.patra.starter.core.error.config.TracingProperties;
import com.patra.starter.core.error.spi.TraceProvider;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.Optional;

/**
 * Trace provider that extracts trace IDs from MDC (Mapped Diagnostic Context).
 * Supports multiple trace header formats commonly used in distributed tracing systems.
 * 
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
public class HeaderBasedTraceProvider implements TraceProvider {
    
    /** Tracing configuration properties */
    private final TracingProperties tracingProperties;
    
    /**
     * Creates a new HeaderBasedTraceProvider with the given configuration.
     * 
     * @param tracingProperties tracing configuration, must not be null
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