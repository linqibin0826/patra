package com.patra.starter.logging.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.common.logging.context.DefaultLogContextEnricher;
import com.patra.common.logging.context.LogContextEnricher;
import com.patra.common.logging.context.TraceContextHolder;
import com.patra.starter.logging.context.DefaultTraceContextHolder;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Unit test validating Reactor-context based MDC propagation in {@link TraceContextGlobalFilter}.
 */
class TraceContextGlobalFilterTest {

  private final TraceContextHolder traceContextHolder = new DefaultTraceContextHolder();
  private final LogContextEnricher logContextEnricher = new DefaultLogContextEnricher();
  private final TraceContextGlobalFilter filter =
      new TraceContextGlobalFilter(traceContextHolder, logContextEnricher);

  @AfterEach
  void clearMdc() {
    traceContextHolder.clearContext();
    logContextEnricher.clear();
    MDC.clear();
  }

  @Test
  @DisplayName("TraceContextGlobalFilter should preserve MDC across Reactor thread hops")
  void shouldPreserveMdcAcrossReactiveBoundaries() {
    String traceId = "trace-reactive-123";
    String spanId = "span-reactive-456";
    String correlationId = "corr-reactive-789";

    ServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/reactive/trace")
                .header("X-Trace-Id", traceId)
                .header("X-Span-Id", spanId)
                .header("X-Correlation-Id", correlationId)
                .build());

    AtomicReference<String> asyncTraceId = new AtomicReference<>();
    AtomicReference<String> asyncSpanId = new AtomicReference<>();
    AtomicReference<String> asyncCorrelationId = new AtomicReference<>();

    GatewayFilterChain chain =
        ex ->
            Mono.deferContextual(
                    contextView ->
                        Mono.fromRunnable(
                            () -> {
                              asyncTraceId.set(MDC.get("traceId"));
                              asyncSpanId.set(MDC.get("spanId"));
                              asyncCorrelationId.set(MDC.get("correlationId"));
                            }))
                .publishOn(Schedulers.boundedElastic())
                .then();

    filter.filter(exchange, chain).block();

    assertThat(asyncTraceId.get()).isEqualTo(traceId);
    assertThat(asyncSpanId.get()).isEqualTo(spanId);
    assertThat(asyncCorrelationId.get()).isEqualTo(correlationId);
  }
}
