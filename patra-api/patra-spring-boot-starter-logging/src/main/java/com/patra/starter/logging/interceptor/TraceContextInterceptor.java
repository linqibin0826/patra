package com.patra.starter.logging.interceptor;

import com.patra.common.logging.context.TraceContextHolder;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Feign request interceptor for propagating trace context to downstream services.
 *
 * <p>This interceptor automatically adds trace headers to all outgoing Feign requests, enabling
 * cross-service trace propagation.
 *
 * <p>Propagated headers:
 *
 * <ul>
 *   <li><b>X-Trace-Id</b>: Distributed trace identifier
 *   <li><b>X-Span-Id</b>: Current span identifier (becomes parent span for downstream)
 *   <li><b>X-Parent-Span-Id</b>: Parent span identifier
 *   <li><b>X-Correlation-Id</b>: Business correlation identifier
 * </ul>
 *
 * <p>Usage: Automatically registered when Feign is on the classpath via {@code
 * TraceContextAutoConfiguration}.
 *
 * @see TraceContextHolder
 * @since 0.1.0
 */
public class TraceContextInterceptor implements RequestInterceptor {

  private static final Logger log = LoggerFactory.getLogger(TraceContextInterceptor.class);

  private static final String HEADER_TRACE_ID = "X-Trace-Id";
  private static final String HEADER_SPAN_ID = "X-Span-Id";
  private static final String HEADER_PARENT_SPAN_ID = "X-Parent-Span-Id";
  private static final String HEADER_CORRELATION_ID = "X-Correlation-Id";

  private final TraceContextHolder traceContextHolder;

  public TraceContextInterceptor(TraceContextHolder traceContextHolder) {
    this.traceContextHolder = traceContextHolder;
  }

  @Override
  public void apply(RequestTemplate template) {
    traceContextHolder
        .getContext()
        .ifPresentOrElse(
            context -> {
              // Propagate trace context to downstream service
              template.header(HEADER_TRACE_ID, context.traceId());

              // Current span ID becomes parent span ID for downstream
              template.header(HEADER_PARENT_SPAN_ID, context.spanId());

              // Generate new span ID for the outgoing request
              // (In production, this should be managed by SkyWalking or similar)
              String newSpanId = java.util.UUID.randomUUID().toString();
              template.header(HEADER_SPAN_ID, newSpanId);

              // Propagate correlation ID if present
              context
                  .correlationId()
                  .ifPresent(
                      correlationId -> template.header(HEADER_CORRELATION_ID, correlationId));

              log.debug(
                  "Feign request trace propagation: {} {} [traceId={}, spanId={} -> {}]",
                  template.method(),
                  template.url(),
                  context.traceId(),
                  context.spanId(),
                  newSpanId);
            },
            () ->
                log.warn(
                    "No trace context available for Feign request: {} {}",
                    template.method(),
                    template.url()));
  }
}
