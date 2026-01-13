package com.patra.starter.httpinterface.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.starter.core.error.config.TracingProperties;
import com.patra.starter.core.error.spi.TraceProvider;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

/// TraceIdClientHttpRequestInterceptor 单元测试
///
/// 测试跟踪标识符在出站请求中的传播功能。
///
/// @author linqibin
/// @since 0.1.0
@ExtendWith(MockitoExtension.class)
@Timeout(value = 2, unit = TimeUnit.SECONDS)
@DisplayName("TraceIdClientHttpRequestInterceptor 单元测试")
class TraceIdClientHttpRequestInterceptorTest {

  @Mock private TraceProvider mockTraceProvider;

  @Mock private HttpRequest mockRequest;

  @Mock private ClientHttpRequestExecution mockExecution;

  @Mock private ClientHttpResponse mockResponse;

  private HttpHeaders requestHeaders;

  @BeforeEach
  void setUp() {
    requestHeaders = new HttpHeaders();
    when(mockRequest.getHeaders()).thenReturn(requestHeaders);
    when(mockRequest.getURI()).thenReturn(URI.create("http://test-service/api/test"));
  }

  @Nested
  @DisplayName("TraceId 注入测试")
  class TraceIdInjectionTests {

    @Test
    @DisplayName("当 TraceId 存在时 - 添加到请求头")
    void shouldAddTraceIdWhenPresent() throws IOException {
      // Given
      var properties = createProperties("traceId");
      var interceptor = new TraceIdClientHttpRequestInterceptor(mockTraceProvider, properties);
      when(mockTraceProvider.getCurrentTraceId()).thenReturn(Optional.of("test-trace-123"));
      when(mockExecution.execute(any(), any())).thenReturn(mockResponse);

      // When
      interceptor.intercept(mockRequest, new byte[0], mockExecution);

      // Then
      assertThat(requestHeaders.getFirst("traceId")).isEqualTo("test-trace-123");
      verify(mockExecution).execute(mockRequest, new byte[0]);
    }

    @Test
    @DisplayName("当 TraceId 不存在时 - 不添加请求头")
    void shouldNotAddHeaderWhenTraceIdAbsent() throws IOException {
      // Given
      var properties = createProperties("traceId");
      var interceptor = new TraceIdClientHttpRequestInterceptor(mockTraceProvider, properties);
      when(mockTraceProvider.getCurrentTraceId()).thenReturn(Optional.empty());
      when(mockExecution.execute(any(), any())).thenReturn(mockResponse);

      // When
      interceptor.intercept(mockRequest, new byte[0], mockExecution);

      // Then
      assertThat(requestHeaders.containsKey("traceId")).isFalse();
      verify(mockExecution).execute(mockRequest, new byte[0]);
    }

    @Test
    @DisplayName("使用自定义 header 名称")
    void shouldUseCustomHeaderName() throws IOException {
      // Given
      var properties = createProperties("X-Custom-Trace");
      var interceptor = new TraceIdClientHttpRequestInterceptor(mockTraceProvider, properties);
      when(mockTraceProvider.getCurrentTraceId()).thenReturn(Optional.of("custom-trace-456"));
      when(mockExecution.execute(any(), any())).thenReturn(mockResponse);

      // When
      interceptor.intercept(mockRequest, new byte[0], mockExecution);

      // Then
      assertThat(requestHeaders.getFirst("X-Custom-Trace")).isEqualTo("custom-trace-456");
      assertThat(requestHeaders.containsKey("traceId")).isFalse();
    }

    @Test
    @DisplayName("使用配置的第一个 header 名称（当配置多个时）")
    void shouldUseFirstConfiguredHeaderName() throws IOException {
      // Given
      var properties = createProperties("X-B3-TraceId", "traceId", "traceparent");
      var interceptor = new TraceIdClientHttpRequestInterceptor(mockTraceProvider, properties);
      when(mockTraceProvider.getCurrentTraceId()).thenReturn(Optional.of("b3-trace-789"));
      when(mockExecution.execute(any(), any())).thenReturn(mockResponse);

      // When
      interceptor.intercept(mockRequest, new byte[0], mockExecution);

      // Then
      assertThat(requestHeaders.getFirst("X-B3-TraceId")).isEqualTo("b3-trace-789");
    }
  }

  @Nested
  @DisplayName("错误处理测试")
  class ErrorHandlingTests {

    @Test
    @DisplayName("TraceProvider 抛出异常时 - 不影响请求执行")
    void shouldNotFailRequestWhenTraceProviderThrows() throws IOException {
      // Given
      var properties = createProperties("traceId");
      var interceptor = new TraceIdClientHttpRequestInterceptor(mockTraceProvider, properties);
      when(mockTraceProvider.getCurrentTraceId()).thenThrow(new RuntimeException("Provider error"));
      when(mockExecution.execute(any(), any())).thenReturn(mockResponse);

      // When
      var response = interceptor.intercept(mockRequest, new byte[0], mockExecution);

      // Then
      assertThat(response).isEqualTo(mockResponse);
      verify(mockExecution).execute(mockRequest, new byte[0]);
      // TraceId 不应被添加
      assertThat(requestHeaders.containsKey("traceId")).isFalse();
    }

    @Test
    @DisplayName("Headers 添加失败时 - 不影响请求执行")
    void shouldContinueWhenHeaderAdditionFails() throws IOException {
      // Given
      var properties = createProperties("traceId");
      var interceptor = new TraceIdClientHttpRequestInterceptor(mockTraceProvider, properties);
      when(mockTraceProvider.getCurrentTraceId()).thenReturn(Optional.of("test-trace"));

      // 使用只读 headers 模拟添加失败
      var readOnlyHeaders = HttpHeaders.readOnlyHttpHeaders(new HttpHeaders());
      when(mockRequest.getHeaders()).thenReturn(readOnlyHeaders);
      when(mockExecution.execute(any(), any())).thenReturn(mockResponse);

      // When
      var response = interceptor.intercept(mockRequest, new byte[0], mockExecution);

      // Then
      assertThat(response).isEqualTo(mockResponse);
      verify(mockExecution).execute(mockRequest, new byte[0]);
    }
  }

  @Nested
  @DisplayName("边界条件测试")
  class EdgeCaseTests {

    @Test
    @DisplayName("配置为空列表时 - 使用默认 header 名称")
    void shouldUseDefaultHeaderWhenConfigEmpty() throws IOException {
      // Given
      var properties = new TracingProperties();
      properties.setHeaderNames(List.of()); // 空列表
      var interceptor = new TraceIdClientHttpRequestInterceptor(mockTraceProvider, properties);
      when(mockTraceProvider.getCurrentTraceId()).thenReturn(Optional.of("default-trace"));
      when(mockExecution.execute(any(), any())).thenReturn(mockResponse);

      // When
      interceptor.intercept(mockRequest, new byte[0], mockExecution);

      // Then
      assertThat(requestHeaders.getFirst("traceId")).isEqualTo("default-trace");
    }

    @Test
    @DisplayName("配置为 null 时 - 使用默认 header 名称")
    void shouldUseDefaultHeaderWhenConfigNull() throws IOException {
      // Given
      var properties = new TracingProperties();
      properties.setHeaderNames(null);
      var interceptor = new TraceIdClientHttpRequestInterceptor(mockTraceProvider, properties);
      when(mockTraceProvider.getCurrentTraceId()).thenReturn(Optional.of("null-config-trace"));
      when(mockExecution.execute(any(), any())).thenReturn(mockResponse);

      // When
      interceptor.intercept(mockRequest, new byte[0], mockExecution);

      // Then
      assertThat(requestHeaders.getFirst("traceId")).isEqualTo("null-config-trace");
    }

    @Test
    @DisplayName("请求体正确传递给 execution")
    void shouldPassRequestBodyToExecution() throws IOException {
      // Given
      var properties = createProperties("traceId");
      var interceptor = new TraceIdClientHttpRequestInterceptor(mockTraceProvider, properties);
      when(mockTraceProvider.getCurrentTraceId()).thenReturn(Optional.empty());
      when(mockExecution.execute(any(), any())).thenReturn(mockResponse);
      byte[] requestBody = "test body".getBytes();

      // When
      interceptor.intercept(mockRequest, requestBody, mockExecution);

      // Then
      verify(mockExecution).execute(eq(mockRequest), eq(requestBody));
    }
  }

  // ===== 辅助方法 =====

  private TracingProperties createProperties(String... headerNames) {
    var properties = new TracingProperties();
    properties.setHeaderNames(List.of(headerNames));
    return properties;
  }
}
