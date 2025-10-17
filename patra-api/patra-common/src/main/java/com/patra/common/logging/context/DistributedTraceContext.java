package com.patra.common.logging.context;

import java.util.Optional;

/**
 * Distributed trace context for cross-service request tracing.
 *
 * <p>This immutable value object carries trace identifiers across service boundaries, enabling
 * complete request chain reconstruction in distributed systems.
 *
 * <p>Key identifiers:
 *
 * <ul>
 *   <li><b>Trace ID</b>: Unique identifier for the entire request flow across all services
 *   <li><b>Span ID</b>: Unique identifier for the current operation within a trace
 *   <li><b>Parent Span ID</b>: Identifier of the calling operation (empty for root spans)
 *   <li><b>Correlation ID</b>: Business-level identifier for grouping related operations (e.g.,
 *       batch processing)
 * </ul>
 *
 * <p>Usage example:
 *
 * <pre>{@code
 * // Create a new trace context for incoming request
 * DistributedTraceContext context = new DistributedTraceContext(
 *     "trace-123",
 *     "span-456",
 *     Optional.of("parent-789"),
 *     Optional.of("batch-2024-01")
 * );
 *
 * // Access trace identifiers
 * String traceId = context.traceId();
 * Optional<String> correlationId = context.correlationId();
 * }</pre>
 *
 * @param traceId Unique identifier for the entire distributed trace (required)
 * @param spanId Unique identifier for the current span/operation (required)
 * @param parentSpanId Optional identifier of the parent span (empty for root spans)
 * @param correlationId Optional business identifier for grouping related operations
 * @see TraceContextHolder
 * @see LogContextEnricher
 * @since 0.1.0
 */
public record DistributedTraceContext(
    String traceId, String spanId, Optional<String> parentSpanId, Optional<String> correlationId) {

  /**
   * Creates a trace context with required identifiers only.
   *
   * @param traceId The trace ID
   * @param spanId The span ID
   * @return A new trace context without parent span or correlation ID
   */
  public static DistributedTraceContext of(String traceId, String spanId) {
    return new DistributedTraceContext(traceId, spanId, Optional.empty(), Optional.empty());
  }

  /**
   * Creates a trace context with correlation ID.
   *
   * @param traceId The trace ID
   * @param spanId The span ID
   * @param correlationId The correlation ID for business grouping
   * @return A new trace context with correlation ID
   */
  public static DistributedTraceContext withCorrelation(
      String traceId, String spanId, String correlationId) {
    return new DistributedTraceContext(
        traceId, spanId, Optional.empty(), Optional.of(correlationId));
  }

  /**
   * Creates a child trace context (with parent span reference).
   *
   * @param traceId The trace ID (inherited from parent)
   * @param spanId The new span ID for the child operation
   * @param parentSpanId The parent span ID
   * @return A new child trace context
   */
  public static DistributedTraceContext child(String traceId, String spanId, String parentSpanId) {
    return new DistributedTraceContext(
        traceId, spanId, Optional.of(parentSpanId), Optional.empty());
  }

  /**
   * Checks if this context has a parent span (not a root span).
   *
   * @return true if parent span ID is present
   */
  public boolean hasParent() {
    return parentSpanId.isPresent();
  }

  /**
   * Checks if this context has a correlation ID.
   *
   * @return true if correlation ID is present
   */
  public boolean hasCorrelation() {
    return correlationId.isPresent();
  }
}
