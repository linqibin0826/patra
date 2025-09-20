package com.patra.starter.core.error.spi;

import java.util.Optional;

/**
 * Provider interface for extracting trace IDs from the current execution context.
 * Supports various tracing systems and contexts (MDC, headers, etc.).
 * 
 * @author linqibin
 * @since 0.1.0
 */
public interface TraceProvider {
    
    /**
     * Gets the current trace ID from the execution context.
     * 
     * @return Optional containing the trace ID if available, empty otherwise
     */
    Optional<String> getCurrentTraceId();
}