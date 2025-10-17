package com.patra.common.logging.context;

import java.util.Optional;

/**
 * Interface for holding and managing distributed trace context.
 *
 * <p>Provides thread-safe access to the current {@link DistributedTraceContext}, typically backed
 * by ThreadLocal or SkyWalking's context propagation mechanism.
 *
 * <p>Implementations must ensure:
 *
 * <ul>
 *   <li>Thread-safety for concurrent access
 *   <li>Proper context cleanup to prevent memory leaks
 *   <li>Integration with distributed tracing systems (e.g., SkyWalking)
 * </ul>
 *
 * <p>Usage example:
 *
 * <pre>{@code
 * TraceContextHolder holder = new DefaultTraceContextHolder();
 *
 * // Set context at request entry point
 * DistributedTraceContext context = DistributedTraceContext.of("trace-123", "span-456");
 * holder.setContext(context);
 *
 * // Access context in any downstream code
 * Optional<DistributedTraceContext> current = holder.getContext();
 * current.ifPresent(ctx -> log.info("Processing trace: {}", ctx.traceId()));
 *
 * // Clear context at request exit
 * holder.clearContext();
 * }</pre>
 *
 * @see DistributedTraceContext
 * @see DefaultTraceContextHolder
 * @since 0.1.0
 */
public interface TraceContextHolder {

  /**
   * Retrieves the current trace context for the executing thread.
   *
   * <p>Returns empty if no context is set (e.g., non-traced operations like scheduled tasks without
   * proper decoration).
   *
   * @return The current trace context, or empty if none is set
   */
  Optional<DistributedTraceContext> getContext();

  /**
   * Sets the trace context for the current thread.
   *
   * <p>Should be called at request entry points (e.g., filters, interceptors) to establish tracing
   * for the entire request flow.
   *
   * @param context The trace context to set (null to clear)
   */
  void setContext(DistributedTraceContext context);

  /**
   * Clears the trace context for the current thread.
   *
   * <p>Must be called at request exit points to prevent context leakage to thread pool threads.
   * Typically invoked in a finally block.
   */
  void clearContext();

  /**
   * Gets the current trace ID, if available.
   *
   * <p>Convenience method equivalent to {@code getContext().map(DistributedTraceContext::traceId)}.
   *
   * @return The current trace ID, or empty if no context is set
   */
  default Optional<String> getTraceId() {
    return getContext().map(DistributedTraceContext::traceId);
  }

  /**
   * Gets the current span ID, if available.
   *
   * <p>Convenience method equivalent to {@code getContext().map(DistributedTraceContext::spanId)}.
   *
   * @return The current span ID, or empty if no context is set
   */
  default Optional<String> getSpanId() {
    return getContext().map(DistributedTraceContext::spanId);
  }

  /**
   * Gets the current correlation ID, if available.
   *
   * <p>Convenience method for accessing business-level correlation identifiers.
   *
   * @return The current correlation ID, or empty if no context is set or no correlation ID exists
   */
  default Optional<String> getCorrelationId() {
    return getContext().flatMap(DistributedTraceContext::correlationId);
  }

  /**
   * Checks if a trace context is currently active.
   *
   * @return true if a trace context is set for the current thread
   */
  default boolean hasContext() {
    return getContext().isPresent();
  }
}
