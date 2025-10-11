package com.patra.starter.core.error.spi;

import java.util.Optional;

/**
 * SPI for retrieving the current trace identifier from the execution context.
 * <p>Implementations may consult MDC, request headers, or other tracing systems.</p>
 */
public interface TraceProvider {

    /**
     * Extracts the trace identifier from the current execution context.
     *
     * @return the trace identifier if available; otherwise empty
     */
    Optional<String> getCurrentTraceId();
}
