package com.patra.starter.logging.context;

import com.patra.common.logging.context.DistributedTraceContext;
import com.patra.common.logging.context.TraceContextHolder;
import java.util.Optional;
import org.apache.skywalking.apm.toolkit.trace.TraceContext;

/**
 * Default implementation of {@link TraceContextHolder} integrated with Apache SkyWalking.
 *
 * <p>This implementation uses SkyWalking's {@link TraceContext} API to retrieve trace identifiers
 * and combines them with a ThreadLocal for storing the full {@link DistributedTraceContext}
 * (including correlation ID).
 *
 * <p>Integration strategy:
 *
 * <ul>
 *   <li>Trace ID and Span ID are retrieved from SkyWalking (if agent is active)
 *   <li>Full context (including correlation ID) is stored in ThreadLocal
 *   <li>Fallback to ThreadLocal-only if SkyWalking agent is not present
 * </ul>
 *
 * <p>Thread-safe and suitable for singleton use.
 *
 * <p>Usage example:
 *
 * <pre>{@code
 * TraceContextHolder holder = new DefaultTraceContextHolder();
 *
 * // Set context at request entry
 * DistributedTraceContext context = DistributedTraceContext.of(
 *     TraceContext.traceId(), // From SkyWalking
 *     TraceContext.segmentId()
 * );
 * holder.setContext(context);
 *
 * // Access anywhere in the request flow
 * holder.getTraceId().ifPresent(traceId ->
 *     log.info("Processing trace: {}", traceId)
 * );
 *
 * // Clear at request exit
 * holder.clearContext();
 * }</pre>
 *
 * @see TraceContextHolder
 * @see DistributedTraceContext
 * @since 0.1.0
 */
public class DefaultTraceContextHolder implements TraceContextHolder {

  private static final ThreadLocal<DistributedTraceContext> CONTEXT_HOLDER = new ThreadLocal<>();
  private final TraceContextExtractor traceContextExtractor;

  public DefaultTraceContextHolder() {
    this(new SkyWalkingTraceContextExtractor());
  }

  DefaultTraceContextHolder(TraceContextExtractor traceContextExtractor) {
    this.traceContextExtractor = traceContextExtractor;
  }

  @Override
  public Optional<DistributedTraceContext> getContext() {
    DistributedTraceContext context = CONTEXT_HOLDER.get();

    if (context != null) {
      return Optional.of(context);
    }

    // Fallback: try to get trace context from SkyWalking
    return createContextFromSkyWalking();
  }

  @Override
  public void setContext(DistributedTraceContext context) {
    if (context == null) {
      clearContext();
    } else {
      CONTEXT_HOLDER.set(context);
    }
  }

  @Override
  public void clearContext() {
    CONTEXT_HOLDER.remove();
  }

  /**
   * Creates a trace context from SkyWalking's active trace (if available).
   *
   * <p>Returns empty if SkyWalking agent is not active or no trace is in progress.
   *
   * @return Trace context from SkyWalking, or empty
   */
  private Optional<DistributedTraceContext> createContextFromSkyWalking() {
    try {
      String traceId = traceContextExtractor.currentTraceId();
      String spanId = traceContextExtractor.currentSpanId();

      // SkyWalking returns "N/A" when agent is not active or no trace exists
      if (traceId != null && !traceId.isEmpty() && !"N/A".equals(traceId) && spanId != null) {

        return Optional.of(DistributedTraceContext.of(traceId, spanId));
      }
    } catch (Exception e) {
      // SkyWalking toolkit may throw exceptions if agent is not properly initialized
      // Fall through to return empty
    }

    return Optional.empty();
  }

  interface TraceContextExtractor {
    String currentTraceId();

    String currentSpanId();
  }

  private static final class SkyWalkingTraceContextExtractor implements TraceContextExtractor {

    @Override
    public String currentTraceId() {
      return TraceContext.traceId();
    }

    @Override
    public String currentSpanId() {
      int spanId = TraceContext.spanId();
      return spanId >= 0 ? String.valueOf(spanId) : null;
    }
  }
}
