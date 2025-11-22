package com.patra.starter.restclient.interceptor;

import java.io.IOException;
import java.util.List;
import org.apache.skywalking.apm.toolkit.trace.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/// REST Client 追踪拦截器。
///
/// <p>自动传播分布式追踪上下文（SkyWalking TraceID 或 MDC）到下游服务。
///
/// <h2>功能特性</h2>
/// <ul>
///   <li>SkyWalking 集成：优先从 SkyWalking 提取 TraceID
///   <li>MDC 降级：如果 SkyWalking 未启用，从 MDC 提取 traceId
///   <li>多 Header 传播：支持多个追踪 Header（如 X-Trace-ID、X-B3-TraceId）
/// </ul>
///
/// <h2>使用示例</h2>
/// <pre>{@code
/// // 配置追踪拦截器
/// patra:
///   rest-client:
///     interceptors:
///       tracing:
///         enabled: true
///         header-names:
///           - X-Trace-ID
///           - X-B3-TraceId
/// }</pre>
///
/// @author linqibin
/// @since 0.1.0
public class TracingInterceptor implements ClientHttpRequestInterceptor {

  private static final Logger log = LoggerFactory.getLogger(TracingInterceptor.class);

  private final List<String> headerNames;

  public TracingInterceptor(List<String> headerNames) {
    this.headerNames = headerNames != null ? headerNames : List.of();
  }

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
      throws IOException {

    // 提取 TraceID
    String traceId = extractTraceId();

    if (traceId != null && !traceId.isEmpty()) {
      // 传播到下游服务
      for (String headerName : headerNames) {
        request.getHeaders().add(headerName, traceId);
      }
      log.debug("Propagated TraceID: {} via headers: {}", traceId, headerNames);
    }

    return execution.execute(request, body);
  }

  /// 提取 TraceID（优先级：SkyWalking > MDC）。
  ///
  /// @return TraceID，如果无法提取则返回 null
  private String extractTraceId() {
    // 1. 优先从 SkyWalking 提取
    try {
      String swTraceId = TraceContext.traceId();
      if (swTraceId != null && !swTraceId.isEmpty()) {
        return swTraceId;
      }
    } catch (Exception e) {
      log.trace("SkyWalking not available, fallback to MDC", e);
    }

    // 2. 降级到 MDC
    return MDC.get("traceId");
  }
}
