package dev.linqibin.starter.restclient.interceptor;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.TimeInterval;
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
/// 记录 HTTP 请求和响应信息，支持配置是否记录 Headers 和 Body。
///
/// ## 功能特性
///
/// - 请求日志：方法、URI、Headers（可选）、Body（可选）
/// - 响应日志：状态码、耗时
/// - 性能监控：记录请求耗时
///
/// ## 使用示例
///
/// ```yaml
/// # 配置日志拦截器
/// patra:
///   rest-client:
///     interceptors:
///       logging:
///         enabled: true
///         log-headers: false
///         log-body: false
/// ```
///
/// @author linqibin
/// @since 0.1.0
public class LoggingInterceptor implements ClientHttpRequestInterceptor {

  private static final Logger log = LoggerFactory.getLogger(LoggingInterceptor.class);

  private final boolean logHeaders;
  private final boolean logBody;
  private final int maxBodyLogLength;

  /// 创建日志拦截器实例。
  ///
  /// @param logHeaders 是否记录 HTTP 头
  /// @param logBody 是否记录请求/响应体
  /// @param maxBodyLogLength Body 日志最大字节数
  public LoggingInterceptor(boolean logHeaders, boolean logBody, int maxBodyLogLength) {
    this.logHeaders = logHeaders;
    this.logBody = logBody;
    this.maxBodyLogLength = maxBodyLogLength;
  }

  /// 拦截 HTTP 请求并记录日志。
  ///
  /// @param request HTTP 请求对象
  /// @param body 请求体字节数组
  /// @param execution 请求执行器
  /// @return HTTP 响应对象
  /// @throws IOException 请求执行异常
  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {

    logRequest(request, body);
    TimeInterval timer = DateUtil.timer();

    ClientHttpResponse response = execution.execute(request, body);

    logResponse(request, response, timer.interval());

    return response;
  }

  /// 记录 HTTP 请求信息。
  ///
  /// 根据配置记录请求方法、URI、Headers 和 Body。
  ///
  /// @param request HTTP 请求对象
  /// @param body 请求体字节数组
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

  /// 记录 HTTP 响应信息。
  ///
  /// 记录响应状态码和请求耗时。
  ///
  /// @param request HTTP 请求对象
  /// @param response HTTP 响应对象
  /// @param duration 请求耗时（毫秒）
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
