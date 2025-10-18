package com.patra.starter.logging.interceptor;

import com.patra.common.logging.context.TraceContextHolder;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * RestTemplate interceptor for propagating trace context to downstream services.
 *
 * <p>This interceptor automatically adds trace headers to all outgoing RestTemplate requests,
 * enabling cross-service trace propagation.
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
 * <p>Usage: Manually register with RestTemplate beans or auto-configured via {@code
 * TraceContextAutoConfiguration}.
 *
 * <pre>{@code
 * @Bean
 * public RestTemplate restTemplate(TraceContextHolder holder) {
 *     RestTemplate template = new RestTemplate();
 *     template.getInterceptors().add(new RestTemplateInterceptor(holder));
 *     return template;
 * }
 * }</pre>
 *
 * @see TraceContextHolder
 * @since 0.1.0
 */
public class RestTemplateInterceptor implements ClientHttpRequestInterceptor {

  private static final Logger log = LoggerFactory.getLogger(RestTemplateInterceptor.class);

  private static final String HEADER_TRACE_ID = "X-Trace-Id";
  private static final String HEADER_SPAN_ID = "X-Span-Id";
  private static final String HEADER_PARENT_SPAN_ID = "X-Parent-Span-Id";
  private static final String HEADER_CORRELATION_ID = "X-Correlation-Id";

  private final TraceContextHolder traceContextHolder;

  public RestTemplateInterceptor(TraceContextHolder traceContextHolder) {
    this.traceContextHolder = traceContextHolder;
  }

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {

    traceContextHolder
        .getContext()
        .ifPresentOrElse(
            context -> {
              // Propagate trace context to downstream service
              request.getHeaders().set(HEADER_TRACE_ID, context.traceId());

              // Current span ID becomes parent span ID for downstream
              request.getHeaders().set(HEADER_PARENT_SPAN_ID, context.spanId());

              // Generate new span ID for the outgoing request
              String newSpanId = java.util.UUID.randomUUID().toString();
              request.getHeaders().set(HEADER_SPAN_ID, newSpanId);

              // Propagate correlation ID if present
              context
                  .correlationId()
                  .ifPresent(
                      correlationId ->
                          request.getHeaders().set(HEADER_CORRELATION_ID, correlationId));

              log.debug(
                  "RestTemplate request trace propagation: {} {} [traceId={}, spanId={} -> {}]",
                  request.getMethod(),
                  request.getURI(),
                  context.traceId(),
                  context.spanId(),
                  newSpanId);
            },
            () ->
                log.warn(
                    "No trace context available for RestTemplate request: {} {}",
                    request.getMethod(),
                    request.getURI()));

    return execution.execute(request, body);
  }
}
