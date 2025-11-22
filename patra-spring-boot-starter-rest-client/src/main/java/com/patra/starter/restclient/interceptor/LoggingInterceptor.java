package com.patra.starter.restclient.interceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/// REST Client 日志拦截器。
///
/// <p>记录 HTTP 请求和响应信息，支持配置是否记录 Headers 和 Body。
///
/// <h2>功能特性</h2>
/// <ul>
///   <li>请求日志：方法、URI、Headers（可选）、Body（可选）
///   <li>响应日志：状态码、耗时
///   <li>性能监控：记录请求耗时
/// </ul>
///
/// <h2>使用示例</h2>
/// <pre>{@code
/// // 配置日志拦截器
/// patra:
///   rest-client:
///     interceptors:
///       logging:
///         enabled: true
///         log-headers: false
///         log-body: false
/// }</pre>
///
/// @author linqibin
/// @since 0.1.0
public class LoggingInterceptor implements ClientHttpRequestInterceptor {

  private static final Logger log = LoggerFactory.getLogger(LoggingInterceptor.class);

  private final boolean logHeaders;
  private final boolean logBody;
  private final int maxBodyLogLength;

  public LoggingInterceptor(boolean logHeaders, boolean logBody, int maxBodyLogLength) {
    this.logHeaders = logHeaders;
    this.logBody = logBody;
    this.maxBodyLogLength = maxBodyLogLength;
  }

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
      throws IOException {

    logRequest(request, body);
    long startTime = System.currentTimeMillis();

    ClientHttpResponse response = execution.execute(request, body);

    long duration = System.currentTimeMillis() - startTime;
    logResponse(request, response, duration);

    return response;
  }

  private void logRequest(HttpRequest request, byte[] body) {
    if (!log.isDebugEnabled()) {
      return;
    }

    log.debug("HTTP {} {}", request.getMethod(), request.getURI());

    if (logHeaders) {
      log.debug("Headers: {}", request.getHeaders());
    }

    if (logBody && body.length > 0) {
      int lengthToLog = Math.min(body.length, maxBodyLogLength);
      String bodyContent = new String(body, 0, lengthToLog, StandardCharsets.UTF_8);

      if (body.length > maxBodyLogLength) {
        log.debug("Body (truncated): {}... (total {} bytes)", bodyContent, body.length);
      } else {
        log.debug("Body: {}", bodyContent);
      }
    }
  }

  private void logResponse(HttpRequest request, ClientHttpResponse response, long duration) {
    if (!log.isDebugEnabled()) {
      return;
    }

    try {
      log.debug(
          "HTTP {} {} -> {} ({} ms)",
          request.getMethod(),
          request.getURI(),
          response.getStatusCode(),
          duration);
    } catch (IOException e) {
      log.warn("Failed to log response status", e);
    }
  }
}
