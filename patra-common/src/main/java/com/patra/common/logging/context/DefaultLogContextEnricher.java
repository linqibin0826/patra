package com.patra.common.logging.context;

import org.slf4j.MDC;

/**
 * Default implementation of {@link LogContextEnricher} using SLF4J MDC.
 *
 * <p>This implementation uses {@link MDC} (Mapped Diagnostic Context) to add trace context
 * information to log messages. MDC is thread-safe and supported by all major logging frameworks
 * (Logback, Log4j2).
 *
 * <p>Thread-safe and suitable for singleton use in Spring applications.
 *
 * <p>Usage example:
 *
 * <pre>{@code
 * LogContextEnricher enricher = new DefaultLogContextEnricher();
 *
 * // In a servlet filter or interceptor
 * try {
 *     DistributedTraceContext context = extractTraceContext(request);
 *     enricher.enrich(context);
 *
 *     // All logs in this thread will include trace context
 *     log.info("Processing request"); // [traceId=xxx][spanId=yyy]
 *
 *     chain.doFilter(request, response);
 * } finally {
 *     enricher.clear(); // Critical: prevent MDC leakage
 * }
 * }</pre>
 *
 * @see LogContextEnricher
 * @see DistributedTraceContext
 * @since 0.1.0
 */
public class DefaultLogContextEnricher implements LogContextEnricher {

  @Override
  public void enrich(DistributedTraceContext context) {
    if (context == null) {
      return;
    }

    // Always set trace ID and span ID
    put(MDC_TRACE_ID, context.traceId());
    put(MDC_SPAN_ID, context.spanId());

    // Set optional parent span ID if present
    context.parentSpanId().ifPresent(parentSpanId -> put(MDC_PARENT_SPAN_ID, parentSpanId));

    // Set optional correlation ID if present
    context.correlationId().ifPresent(correlationId -> put(MDC_CORRELATION_ID, correlationId));
  }

  @Override
  public void clear() {
    remove(MDC_TRACE_ID);
    remove(MDC_SPAN_ID);
    remove(MDC_PARENT_SPAN_ID);
    remove(MDC_CORRELATION_ID);
  }

  @Override
  public void put(String key, String value) {
    if (key != null && value != null) {
      MDC.put(key, value);
    }
  }

  @Override
  public void remove(String key) {
    if (key != null) {
      MDC.remove(key);
    }
  }
}
