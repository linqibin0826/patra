package dev.linqibin.starter.httpinterface.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import dev.linqibin.commons.error.remote.RemoteCallException;
import dev.linqibin.commons.error.trait.StandardErrorTrait;
import dev.linqibin.starter.httpinterface.config.HttpInterfaceProperties;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/// ProblemDetailErrorHandler 单元测试
///
/// 测试 RFC 7807 ProblemDetail 解析、容错模式等功能。
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@DisplayName("ProblemDetailErrorHandler 单元测试")
class ProblemDetailErrorHandlerTest {

  private ObjectMapper objectMapper;

  @Mock private HttpRequest mockRequest;

  @Mock private ClientHttpResponse mockResponse;

  @BeforeEach
  void setUp() {
    // 模拟 Spring Boot 4 JacksonAutoConfiguration 的行为：自动注册 ProblemDetailJacksonMixin
    objectMapper =
        JsonMapper.builder().addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class).build();

    // 设置默认请求 mock
    when(mockRequest.getMethod()).thenReturn(HttpMethod.GET);
    when(mockRequest.getURI()).thenReturn(URI.create("http://test-service/api/test"));
  }

  @Nested
  @DisplayName("ProblemDetail 解析测试")
  class ProblemDetailParsingTests {

    @Test
    @DisplayName("解析标准 ProblemDetail 响应 - 提取 status 和 detail")
    void shouldParseProblemDetailResponse() throws IOException {
      // Given
      var handler = createHandler(true);
      var problemDetailJson =
          """
          {
            "type": "about:blank",
            "title": "Not Found",
            "status": 404,
            "detail": "资源未找到: id=123",
            "instance": "/api/test"
          }
          """;
      setupMockResponse(404, MediaType.APPLICATION_PROBLEM_JSON, problemDetailJson);

      // When & Then
      assertThatThrownBy(() -> handler.handle(mockRequest, mockResponse))
          .isInstanceOf(RemoteCallException.class)
          .satisfies(
              ex -> {
                RemoteCallException rce = (RemoteCallException) ex;
                assertThat(rce.getHttpStatus()).isEqualTo(404);
                assertThat(rce.getMessage()).isEqualTo("资源未找到: id=123");
              });
    }

    @Test
    @DisplayName("从响应头提取 traceId")
    void shouldExtractTraceIdFromHeader() throws IOException {
      // Given
      var handler = createHandler(true);
      var problemDetailJson =
          """
          {
            "type": "about:blank",
            "title": "Error",
            "status": 500,
            "detail": "Internal error"
          }
          """;
      var headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
      headers.set("traceId", "header-trace-123");

      setupMockResponse(500, headers, problemDetailJson);

      // When & Then
      assertThatThrownBy(() -> handler.handle(mockRequest, mockResponse))
          .isInstanceOf(RemoteCallException.class)
          .satisfies(
              ex -> {
                RemoteCallException rce = (RemoteCallException) ex;
                assertThat(rce.getTraceId()).isEqualTo("header-trace-123");
              });
    }

    @Test
    @DisplayName("支持 X-B3-TraceId header 格式")
    void shouldSupportB3TraceIdHeader() throws IOException {
      // Given
      var handler = createHandler(true);
      var problemDetailJson =
          """
          {
            "type": "about:blank",
            "title": "Error",
            "status": 500,
            "detail": "Internal error"
          }
          """;
      var headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
      headers.set("X-B3-TraceId", "b3-trace-456");

      setupMockResponse(500, headers, problemDetailJson);

      // When & Then
      assertThatThrownBy(() -> handler.handle(mockRequest, mockResponse))
          .isInstanceOf(RemoteCallException.class)
          .satisfies(
              ex -> {
                RemoteCallException rce = (RemoteCallException) ex;
                assertThat(rce.getTraceId()).isEqualTo("b3-trace-456");
              });
    }
  }

  @Nested
  @DisplayName("ErrorTraits 解析测试")
  class ErrorTraitsParsingTests {

    @Test
    @DisplayName("解析 ProblemDetail 中的 ErrorTraits")
    void shouldParseErrorTraitsFromProblemDetail() throws IOException {
      // Given
      var handler = createHandler(true);
      var problemDetailJson =
          """
          {
            "type": "about:blank",
            "title": "Not Found",
            "status": 404,
            "detail": "资源未找到",
            "traits": ["NOT_FOUND", "RULE_VIOLATION"]
          }
          """;
      setupMockResponse(404, MediaType.APPLICATION_PROBLEM_JSON, problemDetailJson);

      // When & Then
      assertThatThrownBy(() -> handler.handle(mockRequest, mockResponse))
          .isInstanceOf(RemoteCallException.class)
          .satisfies(
              ex -> {
                RemoteCallException rce = (RemoteCallException) ex;
                assertThat(rce.getErrorTraits())
                    .containsExactlyInAnyOrder(
                        StandardErrorTrait.NOT_FOUND, StandardErrorTrait.RULE_VIOLATION);
              });
    }

    @Test
    @DisplayName("无 traits 字段时返回空集合")
    void shouldReturnEmptyTraitsWhenFieldAbsent() throws IOException {
      // Given
      var handler = createHandler(true);
      var problemDetailJson =
          """
          {
            "type": "about:blank",
            "title": "Not Found",
            "status": 404,
            "detail": "资源未找到"
          }
          """;
      setupMockResponse(404, MediaType.APPLICATION_PROBLEM_JSON, problemDetailJson);

      // When & Then
      assertThatThrownBy(() -> handler.handle(mockRequest, mockResponse))
          .isInstanceOf(RemoteCallException.class)
          .satisfies(
              ex -> {
                RemoteCallException rce = (RemoteCallException) ex;
                assertThat(rce.getErrorTraits()).isEmpty();
              });
    }

    @Test
    @DisplayName("traits 名称大小写不敏感")
    void shouldParseTraitsCaseInsensitively() throws IOException {
      // Given
      var handler = createHandler(true);
      var problemDetailJson =
          """
          {
            "type": "about:blank",
            "title": "Conflict",
            "status": 409,
            "detail": "资源冲突",
            "traits": ["conflict", "rule_violation"]
          }
          """;
      setupMockResponse(409, MediaType.APPLICATION_PROBLEM_JSON, problemDetailJson);

      // When & Then
      assertThatThrownBy(() -> handler.handle(mockRequest, mockResponse))
          .isInstanceOf(RemoteCallException.class)
          .satisfies(
              ex -> {
                RemoteCallException rce = (RemoteCallException) ex;
                assertThat(rce.getErrorTraits())
                    .containsExactlyInAnyOrder(
                        StandardErrorTrait.CONFLICT, StandardErrorTrait.RULE_VIOLATION);
              });
    }

    @Test
    @DisplayName("忽略未知的 trait 名称")
    void shouldIgnoreUnknownTraits() throws IOException {
      // Given
      var handler = createHandler(true);
      var problemDetailJson =
          """
          {
            "type": "about:blank",
            "title": "Error",
            "status": 500,
            "detail": "服务错误",
            "traits": ["NOT_FOUND", "UNKNOWN_TRAIT", "TIMEOUT"]
          }
          """;
      setupMockResponse(500, MediaType.APPLICATION_PROBLEM_JSON, problemDetailJson);

      // When & Then
      assertThatThrownBy(() -> handler.handle(mockRequest, mockResponse))
          .isInstanceOf(RemoteCallException.class)
          .satisfies(
              ex -> {
                RemoteCallException rce = (RemoteCallException) ex;
                assertThat(rce.getErrorTraits())
                    .containsExactlyInAnyOrder(
                        StandardErrorTrait.NOT_FOUND, StandardErrorTrait.TIMEOUT);
              });
    }
  }

  @Nested
  @DisplayName("容错模式测试")
  class TolerantModeTests {

    @Test
    @DisplayName("容错模式下处理非 ProblemDetail 响应 - 包装为 RemoteCallException")
    void shouldWrapNonProblemDetailInTolerantMode() throws IOException {
      // Given
      var handler = createHandler(true); // tolerant = true
      var plainErrorJson =
          """
          {"error": "something went wrong"}
          """;
      setupMockResponse(500, MediaType.APPLICATION_JSON, plainErrorJson);

      // When & Then
      assertThatThrownBy(() -> handler.handle(mockRequest, mockResponse))
          .isInstanceOf(RemoteCallException.class)
          .satisfies(
              ex -> {
                RemoteCallException rce = (RemoteCallException) ex;
                assertThat(rce.getHttpStatus()).isEqualTo(500);
                assertThat(rce.getMessage()).contains("HTTP 500");
              });
    }

    @Test
    @DisplayName("容错模式下处理空响应体")
    void shouldHandleEmptyBodyInTolerantMode() throws IOException {
      // Given
      var handler = createHandler(true);
      var headers = new HttpHeaders();
      headers.setContentType(MediaType.TEXT_PLAIN);

      when(mockResponse.getStatusCode()).thenReturn(HttpStatusCode.valueOf(503));
      when(mockResponse.getHeaders()).thenReturn(headers);
      when(mockResponse.getBody()).thenReturn(new ByteArrayInputStream(new byte[0]));

      // When & Then
      assertThatThrownBy(() -> handler.handle(mockRequest, mockResponse))
          .isInstanceOf(RemoteCallException.class)
          .satisfies(
              ex -> {
                RemoteCallException rce = (RemoteCallException) ex;
                assertThat(rce.getHttpStatus()).isEqualTo(503);
                // 空 body + null statusText 时，回退到纯状态码消息
                assertThat(rce.getMessage()).isEqualTo("HTTP 503");
              });
    }
  }

  @Nested
  @DisplayName("严格模式测试")
  class StrictModeTests {

    @Test
    @DisplayName("严格模式下处理非 ProblemDetail 响应")
    void shouldThrowGenericExceptionInStrictMode() throws IOException {
      // Given
      var handler = createHandler(false); // tolerant = false
      var headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON); // 非 problem+json，不会尝试解析

      when(mockResponse.getStatusCode()).thenReturn(HttpStatusCode.valueOf(500));
      when(mockResponse.getHeaders()).thenReturn(headers);
      // 注意：严格模式下不读取 body，直接返回状态码消息

      // When & Then
      assertThatThrownBy(() -> handler.handle(mockRequest, mockResponse))
          .isInstanceOf(RemoteCallException.class)
          .satisfies(
              ex -> {
                RemoteCallException rce = (RemoteCallException) ex;
                assertThat(rce.getHttpStatus()).isEqualTo(500);
                assertThat(rce.getMessage()).contains("HTTP 500");
              });
    }
  }

  @Nested
  @DisplayName("异常链保留测试")
  class SuppressedExceptionTests {

    @Test
    @DisplayName("ProblemDetail 解析失败时保留原始异常到 suppressed")
    void shouldPreserveSuppressedExceptionWhenParsingFails() throws IOException {
      // Given
      var handler = createHandler(true);
      // 提供无效的 JSON（内容不符合 ProblemDetail 结构）
      var invalidJson = "{ invalid json }";
      setupMockResponse(500, MediaType.APPLICATION_PROBLEM_JSON, invalidJson);

      // When & Then
      assertThatThrownBy(() -> handler.handle(mockRequest, mockResponse))
          .isInstanceOf(RemoteCallException.class)
          .satisfies(
              ex -> {
                RemoteCallException rce = (RemoteCallException) ex;
                // 验证异常被抛出，且包含 suppressed exception
                Throwable[] suppressed = rce.getSuppressed();
                assertThat(suppressed).isNotEmpty();
                // 原始解析异常应该是 Jackson 解析错误
                assertThat(suppressed[0]).isInstanceOf(Exception.class);
              });
    }

    @Test
    @DisplayName("ProblemDetail 解析成功时不应有 suppressed 异常")
    void shouldNotHaveSuppressedExceptionWhenParsingSucceeds() throws IOException {
      // Given
      var handler = createHandler(true);
      var validProblemDetail =
          """
          {
            "type": "about:blank",
            "title": "Error",
            "status": 500,
            "detail": "Server error"
          }
          """;
      setupMockResponse(500, MediaType.APPLICATION_PROBLEM_JSON, validProblemDetail);

      // When & Then
      assertThatThrownBy(() -> handler.handle(mockRequest, mockResponse))
          .isInstanceOf(RemoteCallException.class)
          .satisfies(
              ex -> {
                RemoteCallException rce = (RemoteCallException) ex;
                assertThat(rce.getSuppressed()).isEmpty();
              });
    }

    @Test
    @DisplayName("空响应体时保留 IllegalArgumentException 到 suppressed")
    void shouldPreserveSuppressedExceptionForEmptyBody() throws IOException {
      // Given
      var handler = createHandler(true);
      var headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);

      when(mockResponse.getStatusCode()).thenReturn(HttpStatusCode.valueOf(500));
      when(mockResponse.getHeaders()).thenReturn(headers);
      when(mockResponse.getBody()).thenReturn(new ByteArrayInputStream(new byte[0]));

      // When & Then
      assertThatThrownBy(() -> handler.handle(mockRequest, mockResponse))
          .isInstanceOf(RemoteCallException.class)
          .satisfies(
              ex -> {
                RemoteCallException rce = (RemoteCallException) ex;
                Throwable[] suppressed = rce.getSuppressed();
                assertThat(suppressed).isNotEmpty();
                assertThat(suppressed[0])
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Empty response body");
              });
    }
  }

  @Nested
  @DisplayName("边界条件测试")
  class EdgeCaseTests {

    @Test
    @DisplayName("methodKey 构建 - 包含 HTTP 方法和路径")
    void shouldBuildMethodKeyCorrectly() throws IOException {
      // Given
      var handler = createHandler(true);
      when(mockRequest.getMethod()).thenReturn(HttpMethod.POST);
      when(mockRequest.getURI()).thenReturn(URI.create("http://service/api/users"));

      var problemDetailJson =
          """
          {
            "type": "about:blank",
            "title": "Error",
            "status": 400,
            "detail": "Bad request"
          }
          """;
      setupMockResponse(400, MediaType.APPLICATION_PROBLEM_JSON, problemDetailJson);

      // When & Then
      assertThatThrownBy(() -> handler.handle(mockRequest, mockResponse))
          .isInstanceOf(RemoteCallException.class)
          .satisfies(
              ex -> {
                RemoteCallException rce = (RemoteCallException) ex;
                assertThat(rce.getMethodKey()).isEqualTo("POST /api/users");
              });
    }

    @Test
    @DisplayName("处理 null 请求 - 使用 unknown 作为 methodKey")
    void shouldHandleNullRequest() throws IOException {
      // Given
      var handler = createHandler(true);
      var problemDetailJson =
          """
          {
            "type": "about:blank",
            "title": "Error",
            "status": 500,
            "detail": "error"
          }
          """;
      setupMockResponse(500, MediaType.APPLICATION_PROBLEM_JSON, problemDetailJson);

      // 使用 null 请求
      when(mockRequest.getMethod()).thenReturn(null);
      when(mockRequest.getURI()).thenReturn(null);

      // When & Then
      assertThatThrownBy(() -> handler.handle(mockRequest, mockResponse))
          .isInstanceOf(RemoteCallException.class)
          .satisfies(
              ex -> {
                RemoteCallException rce = (RemoteCallException) ex;
                assertThat(rce.getMethodKey()).isEqualTo("? ?");
              });
    }
  }

  // ===== 辅助方法 =====

  private ProblemDetailErrorHandler createHandler(boolean tolerant) {
    var errorHandling = new HttpInterfaceProperties.ErrorHandlingProperties();
    errorHandling.setTolerant(tolerant);
    errorHandling.setProblemDetailEnabled(true);
    errorHandling.setMaxErrorBodySize(10240);
    return new ProblemDetailErrorHandler(objectMapper, errorHandling);
  }

  private void setupMockResponse(int status, MediaType contentType, String body)
      throws IOException {
    var headers = new HttpHeaders();
    headers.setContentType(contentType);
    setupMockResponse(status, headers, body);
  }

  private void setupMockResponse(int status, HttpHeaders headers, String body) throws IOException {
    when(mockResponse.getStatusCode()).thenReturn(HttpStatusCode.valueOf(status));
    when(mockResponse.getHeaders()).thenReturn(headers);
    when(mockResponse.getBody())
        .thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
  }
}
