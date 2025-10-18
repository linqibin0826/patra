package com.patra.common.logging.context;

/**
 * Interface for enriching MDC (Mapped Diagnostic Context) with trace information.
 *
 * <p>MDC provides a thread-local map for adding contextual information to log messages. This
 * interface standardizes MDC key names and ensures consistent logging across all services.
 *
 * <p>Standard MDC keys:
 *
 * <ul>
 *   <li><b>traceId</b>: Distributed trace identifier
 *   <li><b>spanId</b>: Current span/operation identifier
 *   <li><b>parentSpanId</b>: Parent span identifier (for nested operations)
 *   <li><b>correlationId</b>: Business-level correlation identifier
 * </ul>
 *
 * <p>Usage example:
 *
 * <pre>{@code
 * LogContextEnricher enricher = new DefaultLogContextEnricher();
 * DistributedTraceContext context = DistributedTraceContext.of("trace-123", "span-456");
 *
 * // Enrich MDC with trace context
 * enricher.enrich(context);
 *
 * // All subsequent logs will include trace identifiers in MDC
 * log.info("Processing request"); // Logback pattern will include [traceId=trace-123][spanId=span-456]
 *
 * // Clear MDC when request completes
 * enricher.clear();
 * }</pre>
 *
 * @see DistributedTraceContext
 * @see DefaultLogContextEnricher
 * @since 0.1.0
 */
public interface LogContextEnricher {

  /** MDC key for trace ID. */
  String MDC_TRACE_ID = "traceId";

  /** MDC key for span ID. */
  String MDC_SPAN_ID = "spanId";

  /** MDC key for parent span ID. */
  String MDC_PARENT_SPAN_ID = "parentSpanId";

  /** MDC key for correlation ID. */
  String MDC_CORRELATION_ID = "correlationId";

  /**
   * Enriches MDC with trace context information.
   *
   * <p>Adds all available trace identifiers to MDC for inclusion in log messages.
   *
   * @param context The trace context to add to MDC (null-safe)
   */
  void enrich(DistributedTraceContext context);

  /**
   * Clears all trace context keys from MDC.
   *
   * <p>Should be called in finally blocks to prevent MDC leakage across thread pool threads.
   */
  void clear();

  /**
   * Enriches MDC with a custom key-value pair.
   *
   * <p>Useful for adding service-specific or layer-specific context (e.g., service=patra-registry,
   * layer=adapter).
   *
   * @param key The MDC key
   * @param value The value to add (null to remove the key)
   */
  void put(String key, String value);

  /**
   * Removes a custom key from MDC.
   *
   * @param key The MDC key to remove
   */
  void remove(String key);
}
