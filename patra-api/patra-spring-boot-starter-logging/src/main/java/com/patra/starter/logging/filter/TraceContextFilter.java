package com.patra.starter.logging.filter;

import com.patra.common.logging.context.DistributedTraceContext;
import com.patra.common.logging.context.LogContextEnricher;
import com.patra.common.logging.context.TraceContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter for extracting and propagating distributed trace context.
 *
 * <p>This filter runs at the highest precedence to ensure trace context is available for all
 * downstream processing.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Extract trace context from HTTP headers (X-Trace-Id, X-Span-Id, X-Correlation-Id)
 *   <li>Generate new trace ID if missing (fallback behavior)
 *   <li>Store context in {@link TraceContextHolder}
 *   <li>Enrich MDC via {@link LogContextEnricher}
 *   <li>Clear context after request completes
 * </ul>
 *
 * <p>HTTP Headers:
 *
 * <ul>
 *   <li><b>X-Trace-Id</b>: Distributed trace identifier (generated if missing)
 *   <li><b>X-Span-Id</b>: Current span identifier (generated if missing)
 *   <li><b>X-Parent-Span-Id</b>: Parent span identifier (optional)
 *   <li><b>X-Correlation-Id</b>: Business correlation identifier (optional)
 * </ul>
 *
 * @see TraceContextHolder
 * @see LogContextEnricher
 * @since 0.1.0
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceContextFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(TraceContextFilter.class);

  private static final String HEADER_TRACE_ID = "X-Trace-Id";
  private static final String HEADER_SPAN_ID = "X-Span-Id";
  private static final String HEADER_PARENT_SPAN_ID = "X-Parent-Span-Id";
  private static final String HEADER_CORRELATION_ID = "X-Correlation-Id";

  private final TraceContextHolder traceContextHolder;
  private final LogContextEnricher logContextEnricher;

  public TraceContextFilter(
      TraceContextHolder traceContextHolder, LogContextEnricher logContextEnricher) {
    this.traceContextHolder = traceContextHolder;
    this.logContextEnricher = logContextEnricher;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    try {
      // Extract or generate trace context
      DistributedTraceContext context = extractTraceContext(request);

      // Store in holder and enrich MDC
      traceContextHolder.setContext(context);
      logContextEnricher.enrich(context);

      // Log request with trace context
      log.debug(
          "Incoming request: {} {} [traceId={}, spanId={}]",
          request.getMethod(),
          request.getRequestURI(),
          context.traceId(),
          context.spanId());

      // Propagate to response headers
      response.setHeader(HEADER_TRACE_ID, context.traceId());
      response.setHeader(HEADER_SPAN_ID, context.spanId());

      // Continue filter chain
      filterChain.doFilter(request, response);

    } finally {
      // Critical: clear context to prevent leakage
      logContextEnricher.clear();
      traceContextHolder.clearContext();
    }
  }

  /**
   * Extracts trace context from request headers or generates new context.
   *
   * <p>Fallback behavior (T038): If trace ID is missing, generates a new one with WARN log.
   *
   * @param request The HTTP request
   * @return Trace context (never null)
   */
  private DistributedTraceContext extractTraceContext(HttpServletRequest request) {
    String traceId = request.getHeader(HEADER_TRACE_ID);
    String spanId = request.getHeader(HEADER_SPAN_ID);
    String parentSpanId = request.getHeader(HEADER_PARENT_SPAN_ID);
    String correlationId = request.getHeader(HEADER_CORRELATION_ID);

    // Fallback: generate new trace ID if missing
    if (traceId == null || traceId.isBlank()) {
      traceId = generateTraceId();
      log.warn(
          "Missing trace ID in request headers, generated new trace ID: {} [uri={}]",
          traceId,
          request.getRequestURI());
    }

    // Generate span ID if missing
    if (spanId == null || spanId.isBlank()) {
      spanId = generateSpanId();
    }

    return new DistributedTraceContext(
        traceId,
        spanId,
        Optional.ofNullable(parentSpanId).filter(s -> !s.isBlank()),
        Optional.ofNullable(correlationId).filter(s -> !s.isBlank()));
  }

  /**
   * Generates a new trace ID.
   *
   * @return UUID-based trace ID
   */
  private String generateTraceId() {
    return UUID.randomUUID().toString();
  }

  /**
   * Generates a new span ID.
   *
   * @return UUID-based span ID
   */
  private String generateSpanId() {
    return UUID.randomUUID().toString();
  }
}
