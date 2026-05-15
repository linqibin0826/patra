package com.patra.starter.httpinterface.error;

import com.patra.starter.httpinterface.config.HttpInterfaceProperties;
import dev.linqibin.commons.error.problem.ErrorKeys;
import dev.linqibin.commons.error.remote.RemoteCallException;
import dev.linqibin.commons.error.trait.ErrorTrait;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.ProblemDetail;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/// RestClient 错误处理器，解析 RFC 7807 ProblemDetail 响应并转换为 {@link RemoteCallException}
///
/// 此处理器实现 {@link RestClient.ResponseSpec.ErrorHandler} 接口，提供与 Feign 版本相同的智能错误处理策略：
///
/// - 优先尝试解析 RFC 7807 ProblemDetail 格式的错误响应
/// - 在宽容模式下，对非 ProblemDetail 响应进行包装而非抛出原始异常
/// - 自动提取并传播跟踪标识符（支持多种 trace header）
/// - 解析并传播 ErrorTraits 语义特征
///
/// **使用方式：**
/// ```java
/// RestClient client = RestClient.builder()
///     .defaultStatusHandler(HttpStatusCode::isError, problemDetailErrorHandler)
///     .build();
/// ```
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public class ProblemDetailErrorHandler implements RestClient.ResponseSpec.ErrorHandler {

  private final ObjectMapper objectMapper;
  private final HttpInterfaceProperties.ErrorHandlingProperties properties;

  /// 构造 ProblemDetail 错误处理器
  ///
  /// @param objectMapper JSON 序列化器
  /// @param properties 错误处理配置属性
  public ProblemDetailErrorHandler(
      ObjectMapper objectMapper, HttpInterfaceProperties.ErrorHandlingProperties properties) {
    this.objectMapper = objectMapper;
    this.properties = properties;
  }

  @Override
  public void handle(HttpRequest request, ClientHttpResponse response) throws IOException {
    String methodKey = buildMethodKey(request);
    int statusCode = response.getStatusCode().value();

    log.debug(
        "处理远程调用错误: method={} status={} contentType={}",
        methodKey,
        statusCode,
        response.getHeaders().getContentType());

    byte[] bodyBytes = readResponseBody(response);

    // 尝试解析 ProblemDetail 格式
    if (isProblemDetailResponse(response)) {
      Exception parseException = null;
      RemoteCallException exception = null;

      try {
        exception = tryParseProblemDetail(methodKey, response, bodyBytes);
      } catch (Exception ex) {
        parseException = ex;
        log.debug("ProblemDetail 解析失败", ex);
      }

      if (exception != null) {
        throw exception;
      }

      // ProblemDetail 解析失败，创建 fallback 异常并保留原始解析异常
      RemoteCallException fallback = handleNonProblemDetailResponse(methodKey, response, bodyBytes);
      if (parseException != null) {
        fallback.addSuppressed(parseException);
      }
      throw fallback;
    }

    // 非 ProblemDetail 响应
    throw handleNonProblemDetailResponse(methodKey, response, bodyBytes);
  }

  /// 处理非 ProblemDetail 响应
  ///
  /// @param methodKey 方法标识
  /// @param response HTTP 响应
  /// @param bodyBytes 已读取的响应体字节数组
  /// @return 包装后的异常
  private RemoteCallException handleNonProblemDetailResponse(
      String methodKey, ClientHttpResponse response, byte[] bodyBytes) throws IOException {
    // 宽容模式：包装非 ProblemDetail 响应
    if (properties.isTolerant()) {
      return handleTolerantMode(methodKey, response, bodyBytes);
    }

    // 严格模式：抛出通用异常
    int statusCode = response.getStatusCode().value();
    String message = buildFallbackMessage(response, bodyBytes);
    return new RemoteCallException(
        statusCode, message, methodKey, extractTraceId(response.getHeaders()));
  }

  /// 尝试解析 ProblemDetail 响应
  ///
  /// 使用 Jackson 直接反序列化 ProblemDetail。Spring Boot 4 的 JacksonAutoConfiguration
  /// 已通过 ProblemDetailJsonMapperBuilderCustomizer 自动注册 ProblemDetailJacksonMixin，
  /// 确保扩展属性能正确映射到 properties Map。
  ///
  /// @param methodKey 方法标识
  /// @param response HTTP 响应
  /// @param bodyBytes 已读取的响应体字节数组
  /// @return 解码后的异常
  /// @throws Exception 解析失败时抛出异常（由调用方处理并保留到 suppressed）
  private RemoteCallException tryParseProblemDetail(
      String methodKey, ClientHttpResponse response, byte[] bodyBytes) throws Exception {
    if (bodyBytes == null || bodyBytes.length == 0) {
      throw new IllegalArgumentException("Empty response body, cannot parse ProblemDetail");
    }

    long start = System.nanoTime();
    ProblemDetail problemDetail = objectMapper.readValue(bodyBytes, ProblemDetail.class);
    long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

    log.debug("ProblemDetail 解析成功: status={} duration={}ms", problemDetail.getStatus(), durationMs);

    // 从响应头提取 traceId 并补充到 ProblemDetail
    String traceId = extractTraceId(response.getHeaders());
    if (traceId != null) {
      Map<String, Object> props = problemDetail.getProperties();
      if (props == null || props.get(ErrorKeys.TRACE_ID) == null) {
        problemDetail.setProperty(ErrorKeys.TRACE_ID, traceId);
      }
    }

    return createExceptionFromProblemDetail(problemDetail, methodKey);
  }

  /// 从 ProblemDetail 创建 RemoteCallException
  ///
  /// @param problemDetail 解析后的 ProblemDetail
  /// @param methodKey 方法标识
  /// @return 构造的异常
  private RemoteCallException createExceptionFromProblemDetail(
      ProblemDetail problemDetail, String methodKey) {
    Map<String, Object> properties =
        problemDetail.getProperties() != null ? problemDetail.getProperties() : Map.of();

    String errorCode = (String) properties.get(ErrorKeys.CODE);
    String traceId = (String) properties.get(ErrorKeys.TRACE_ID);
    Set<ErrorTrait> errorTraits =
        RemoteCallException.parseErrorTraits(properties.get(ErrorKeys.TRAITS));

    return new RemoteCallException(
        errorCode,
        problemDetail.getStatus(),
        problemDetail.getDetail(),
        methodKey,
        traceId,
        new HashMap<>(properties),
        errorTraits);
  }

  /// 宽容模式处理：将非 ProblemDetail 响应包装为 RemoteCallException
  ///
  /// @param methodKey 方法标识
  /// @param response HTTP 响应
  /// @param bodyBytes 已读取的响应体字节数组
  /// @return 包装后的异常
  private RemoteCallException handleTolerantMode(
      String methodKey, ClientHttpResponse response, byte[] bodyBytes) throws IOException {
    String traceId = extractTraceId(response.getHeaders());
    String message = buildFallbackMessage(response, bodyBytes);

    log.debug("容错模式: 包装非 ProblemDetail 响应, method={}", methodKey);

    return new RemoteCallException(response.getStatusCode().value(), message, methodKey, traceId);
  }

  /// 读取响应体（带大小限制）
  ///
  /// @param response HTTP 响应
  /// @return 响应体字节数组，如果响应体为空则返回空数组
  private byte[] readResponseBody(ClientHttpResponse response) throws IOException {
    var body = response.getBody();
    if (body == null) {
      return new byte[0];
    }
    int maxSize = properties.getMaxErrorBodySize();
    return body.readNBytes(maxSize);
  }

  /// 构建降级错误消息
  ///
  /// @param response HTTP 响应
  /// @param bodyBytes 已读取的响应体字节数组
  /// @return 错误消息
  private String buildFallbackMessage(ClientHttpResponse response, byte[] bodyBytes)
      throws IOException {
    String statusText = response.getStatusText();
    if (statusText != null && !statusText.isBlank()) {
      return "HTTP " + response.getStatusCode().value() + " " + statusText;
    }

    if (bodyBytes != null && bodyBytes.length > 0) {
      String body = new String(bodyBytes, StandardCharsets.UTF_8);
      if (body.length() > 200) {
        body = body.substring(0, 200) + "...";
      }
      return "HTTP " + response.getStatusCode().value() + ": " + body;
    }

    return "HTTP " + response.getStatusCode().value();
  }

  /// 判断响应是否为 ProblemDetail 格式
  ///
  /// @param response HTTP 响应
  /// @return 如果是 application/problem+json 则返回 true
  private boolean isProblemDetailResponse(ClientHttpResponse response) {
    var contentType = response.getHeaders().getContentType();
    if (contentType == null) {
      return false;
    }
    String type = contentType.toString().toLowerCase();
    return type.contains("application/problem+json");
  }

  /// 从响应头提取跟踪标识符
  ///
  /// @param headers HTTP 响应头
  /// @return 跟踪标识符，未找到返回 null
  private String extractTraceId(HttpHeaders headers) {
    String[] traceHeaders = {"traceId", "X-B3-TraceId", "traceparent", "X-Trace-Id"};
    for (String header : traceHeaders) {
      String value = headers.getFirst(header);
      if (value != null && !value.isBlank()) {
        return value.trim();
      }
    }
    return null;
  }

  /// 构建方法标识
  ///
  /// @param request HTTP 请求
  /// @return 方法标识字符串
  private String buildMethodKey(HttpRequest request) {
    if (request == null) {
      return "unknown";
    }
    String methodName = request.getMethod() != null ? request.getMethod().name() : "?";
    String path = request.getURI() != null ? request.getURI().getPath() : "?";
    return methodName + " " + path;
  }
}
