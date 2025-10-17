package com.patra.starter.logging.gateway;

import com.patra.common.logging.context.DistributedTraceContext;
import com.patra.common.logging.context.LogContextEnricher;
import com.patra.common.logging.context.TraceContextHolder;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * WebFlux-compatible global filter for Spring Cloud Gateway trace context propagation.
 *
 * <p>Implements FR-003: Automatic trace context propagation across all layers.
 *
 * <p>Implements SC-002: 100% trace ID coverage for synchronous operations.
 *
 * <h3>Responsibilities:</h3>
 *
 * <ul>
 *   <li>Extract trace context from incoming request headers (X-Trace-Id, X-Correlation-Id, etc.)
 *   <li>Generate new trace IDs if missing (entry point behavior)
 *   <li>Enrich Reactor Context with trace data (WebFlux-safe MDC alternative)
 *   <li>Propagate trace headers to downstream services
 *   <li>Clear context after request completes (prevent leakage)
 * </ul>
 *
 * <h3>WebFlux vs Servlet Differences:</h3>
 *
 * <ul>
 *   <li><strong>Servlet (TraceContextFilter)</strong>: Uses ThreadLocal MDC (safe for
 *       thread-per-request model)
 *   <li><strong>WebFlux (this class)</strong>: Uses Reactor Context (safe for async/non-blocking
 *       model)
 * </ul>
 *
 * <h3>Trace Headers:</h3>
 *
 * <ul>
 *   <li>X-Trace-Id: Distributed trace identifier (UUID)
 *   <li>X-Span-Id: Current span identifier (UUID)
 *   <li>X-Parent-Span-Id: Parent span identifier (optional)
 *   <li>X-Correlation-Id: Business correlation identifier (optional)
 * </ul>
 *
 * <h3>Execution Order:</h3>
 *
 * <ul>
 *   <li>Runs at HIGHEST_PRECEDENCE (-100) to ensure trace context available for all downstream
 *       filters
 *   <li>Executes before routing, load balancing, and custom filters
 * </ul>
 *
 * <h3>Usage Example:</h3>
 *
 * <pre>{@code
 * // Incoming request to gateway:
 * GET /patra-registry/provenance/pubmed
 * Headers: X-Trace-Id: abc-123
 *
 * // Gateway forwards to patra-registry:
 * GET /provenance/pubmed
 * Headers: X-Trace-Id: abc-123, X-Span-Id: def-456, X-Parent-Span-Id: (gateway-span)
 * }</pre>
 *
 * @see TraceContextHolder
 * @see LogContextEnricher
 * @since 0.1.0 (Phase 5 - User Story 3)
 */
public class TraceContextGlobalFilter implements GlobalFilter, Ordered {

  private static final Logger log = LoggerFactory.getLogger(TraceContextGlobalFilter.class);

  // HTTP header constants for trace propagation
  private static final String HEADER_TRACE_ID = "X-Trace-Id";
  private static final String HEADER_SPAN_ID = "X-Span-Id";
  private static final String HEADER_PARENT_SPAN_ID = "X-Parent-Span-Id";
  private static final String HEADER_CORRELATION_ID = "X-Correlation-Id";

  private final TraceContextHolder traceContextHolder;
  private final LogContextEnricher logContextEnricher;

  public TraceContextGlobalFilter(
      TraceContextHolder traceContextHolder, LogContextEnricher logContextEnricher) {
    this.traceContextHolder = traceContextHolder;
    this.logContextEnricher = logContextEnricher;
    log.info(
        "Initialized TraceContextGlobalFilter for Spring Cloud Gateway (Phase 5 - US3: FR-003,"
            + " SC-002)");
  }

  /**
   * Filters gateway requests with trace context extraction and propagation.
   *
   * <p>Execution flow:
   *
   * <ol>
   *   <li>Extract trace context from request headers
   *   <li>Generate new IDs if missing (gateway is entry point)
   *   <li>Enrich Reactor Context with trace data
   *   <li>Add trace headers to downstream request
   *   <li>Continue filter chain
   *   <li>Clear context on completion (cleanup)
   * </ol>
   *
   * @param exchange ServerWebExchange containing request/response
   * @param chain GatewayFilterChain for continuing filter chain
   * @return Mono<Void> representing async completion
   */
  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    ServerHttpRequest request = exchange.getRequest();

    // Extract trace context from incoming headers
    String extractedTraceId = extractHeader(request, HEADER_TRACE_ID);
    String extractedSpanId = extractHeader(request, HEADER_SPAN_ID);
    String parentSpanId = extractHeader(request, HEADER_PARENT_SPAN_ID);
    String correlationId = extractHeader(request, HEADER_CORRELATION_ID);

    // Generate new IDs if missing (gateway is entry point)
    final boolean generatedTraceId;
    final String traceId;
    if (extractedTraceId == null || extractedTraceId.isBlank()) {
      traceId = UUID.randomUUID().toString();
      generatedTraceId = true;
      log.warn(
          "Trace ID missing from request - generated new trace ID: {} (check upstream trace"
              + " propagation)",
          traceId);
    } else {
      traceId = extractedTraceId;
      generatedTraceId = false;
    }

    final String spanId;
    if (extractedSpanId == null || extractedSpanId.isBlank()) {
      spanId = UUID.randomUUID().toString();
    } else {
      spanId = extractedSpanId;
    }

    // Create trace context (wrap nullable values in Optional)
    DistributedTraceContext traceContext =
        new DistributedTraceContext(
            traceId, spanId, Optional.ofNullable(parentSpanId), Optional.ofNullable(correlationId));

    // Store in TraceContextHolder (SkyWalking integration)
    traceContextHolder.setContext(traceContext);

    // Enrich MDC for logging (WebFlux-safe via Reactor Context)
    logContextEnricher.enrich(traceContext);

    // Log trace context extraction (DEBUG level)
    log.debug(
        "Gateway trace context extracted: traceId={}, spanId={}, correlationId={}, generated={}",
        traceId,
        spanId,
        correlationId,
        generatedTraceId);

    // Mutate request to add trace headers for downstream services
    ServerHttpRequest mutatedRequest =
        request
            .mutate()
            .headers(
                headers -> {
                  headers.set(HEADER_TRACE_ID, traceId);
                  headers.set(HEADER_SPAN_ID, spanId);
                  if (parentSpanId != null) {
                    headers.set(HEADER_PARENT_SPAN_ID, parentSpanId);
                  }
                  if (correlationId != null) {
                    headers.set(HEADER_CORRELATION_ID, correlationId);
                  }
                })
            .build();

    // Replace request in exchange
    ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();

    // Continue filter chain with enriched context
    return chain
        .filter(mutatedExchange)
        .doFinally(
            signalType -> {
              // Cleanup: Clear trace context after request completes (prevent leakage)
              traceContextHolder.clearContext();
              logContextEnricher.clear();

              log.trace(
                  "Gateway trace context cleared: traceId={}, signal={}", traceId, signalType);
            });
  }

  /**
   * Extracts header value from request (case-insensitive).
   *
   * @param request ServerHttpRequest to extract header from
   * @param headerName Header name (e.g., "X-Trace-Id")
   * @return Header value or null if not present
   */
  private String extractHeader(ServerHttpRequest request, String headerName) {
    HttpHeaders headers = request.getHeaders();
    return headers.getFirst(headerName);
  }

  /**
   * Returns execution order (HIGHEST_PRECEDENCE).
   *
   * <p>This filter must run before:
   *
   * <ul>
   *   <li>Routing filters (route selection)
   *   <li>Load balancing filters (service selection)
   *   <li>Custom business logic filters
   *   <li>Circuit breaker filters
   * </ul>
   *
   * <p>This ensures trace context is available for all downstream operations.
   *
   * @return Ordered.HIGHEST_PRECEDENCE (-100)
   */
  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }
}
