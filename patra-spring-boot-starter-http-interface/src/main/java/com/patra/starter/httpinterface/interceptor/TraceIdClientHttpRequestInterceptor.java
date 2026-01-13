package com.patra.starter.httpinterface.interceptor;

import com.patra.starter.core.error.config.TracingProperties;
import com.patra.starter.core.error.spi.TraceProvider;
import java.io.IOException;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/// RestClient 拦截器，向下游传播当前跟踪标识符
///
/// 使用来自 {@link TracingProperties} 的可配置 Header 名称以及 {@link TraceProvider} SPI，
/// 使其能够适配不同的跟踪系统（如 OpenTelemetry、B3、自定义系统等）。
///
/// **与 Feign RequestInterceptor 的对应关系：**
///
/// | Feign | RestClient |
/// |-------|------------|
/// | `RequestInterceptor.apply(RequestTemplate)` | `ClientHttpRequestInterceptor.intercept()` |
/// | `RequestTemplate.header(name, value)` | `HttpRequest.getHeaders().add(name, value)` |
///
/// **工作流程：**
/// 1. 从 {@link TraceProvider} 获取当前线程的 TraceId
/// 2. 如果存在，添加到出站请求的 Header 中
/// 3. 传播失败不会导致请求失败（仅记录警告日志）
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public class TraceIdClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

  private final TraceProvider traceProvider;
  private final TracingProperties tracingProperties;

  /// 构造跟踪标识符请求拦截器
  ///
  /// @param traceProvider 跟踪标识符提供者 SPI
  /// @param tracingProperties 跟踪配置属性
  public TraceIdClientHttpRequestInterceptor(
      TraceProvider traceProvider, TracingProperties tracingProperties) {
    this.traceProvider = traceProvider;
    this.tracingProperties = tracingProperties;
  }

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    try {
      Optional<String> traceId = traceProvider.getCurrentTraceId();

      if (traceId.isPresent()) {
        String headerName = getTraceHeaderName();
        request.getHeaders().add(headerName, traceId.get());

        log.debug(
            "Added TraceId to request: {}={} url={}",
            headerName,
            traceId.get(),
            request.getURI());
      } else {
        log.debug("No TraceId available, url={}", request.getURI());
      }

    } catch (Exception e) {
      // 传播失败不应导致请求失败
      log.warn(
          "Failed to propagate TraceId, url={}, error={}", request.getURI(), e.getMessage());
    }

    return execution.execute(request, body);
  }

  /// 获取用于跟踪传播的 Header 名称
  ///
  /// @return 第一个配置的值或默认值 "traceId"
  private String getTraceHeaderName() {
    if (tracingProperties.getHeaderNames() != null
        && !tracingProperties.getHeaderNames().isEmpty()) {
      return tracingProperties.getHeaderNames().get(0);
    }

    // 未配置时使用默认 Header 名称
    return "traceId";
  }
}
