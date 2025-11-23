package com.patra.starter.restclient.interceptor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/// REST Client 指标拦截器。
///
/// <p>收集 HTTP 请求的性能指标（成功/失败计数、请求耗时）。
///
/// <h2>功能特性</h2>
/// <ul>
///   <li>成功计数器：{@code rest_client_requests_success_total}
///   <li>失败计数器：{@code rest_client_requests_failure_total}
///   <li>请求耗时：{@code rest_client_request_duration_seconds}
/// </ul>
///
/// <h2>使用示例</h2>
/// <pre>{@code
/// // 配置指标拦截器
/// patra:
///   rest-client:
///     interceptors:
///       metrics:
///         enabled: true
/// }</pre>
///
/// @author linqibin
/// @since 0.1.0
public class MetricsInterceptor implements ClientHttpRequestInterceptor {

  private final Counter successCounter;
  private final Counter failureCounter;
  private final Timer requestTimer;

  public MetricsInterceptor(MeterRegistry meterRegistry) {
    this.successCounter =
        Counter.builder("rest_client_requests_success_total")
            .description("Total successful HTTP requests")
            .register(meterRegistry);

    this.failureCounter =
        Counter.builder("rest_client_requests_failure_total")
            .description("Total failed HTTP requests")
            .register(meterRegistry);

    this.requestTimer =
        Timer.builder("rest_client_request_duration_seconds")
            .description("HTTP request duration")
            .register(meterRegistry);
  }

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {

    Timer.Sample sample = Timer.start();

    try {
      ClientHttpResponse response = execution.execute(request, body);

      // 检查响应状态码
      if (response.getStatusCode().is2xxSuccessful()) {
        successCounter.increment();
      } else {
        failureCounter.increment();
      }

      sample.stop(requestTimer);
      return response;
    } catch (IOException e) {
      failureCounter.increment();
      sample.stop(requestTimer);
      throw e;
    }
  }
}
