package com.patra.starter.httpinterface.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;

/// StaleConnectionRetryInterceptor 单元测试
///
/// 验证 stale connection 重试拦截器的正确行为
@DisplayName("StaleConnectionRetryInterceptor 单元测试")
class StaleConnectionRetryInterceptorTest {

  private StaleConnectionRetryInterceptor interceptor;
  private ClientHttpRequestExecution execution;
  private MockClientHttpRequest request;
  private ClientHttpResponse mockResponse;
  private byte[] body;

  @BeforeEach
  void setUp() throws Exception {
    interceptor = new StaleConnectionRetryInterceptor(2);
    execution = mock(ClientHttpRequestExecution.class);
    request = new MockClientHttpRequest(HttpMethod.GET, new URI("http://test-service/api"));
    mockResponse = mock(ClientHttpResponse.class);
    body = new byte[0];
  }

  @Nested
  @DisplayName("正常请求处理")
  class NormalRequestTests {

    @Test
    @DisplayName("成功请求应直接返回响应")
    void shouldReturnResponseOnSuccess() throws Exception {
      // Given
      when(execution.execute(any(), any())).thenReturn(mockResponse);

      // When
      ClientHttpResponse response = interceptor.intercept(request, body, execution);

      // Then
      assertThat(response).isSameAs(mockResponse);
      verify(execution, times(1)).execute(any(), any());
    }

    @Test
    @DisplayName("非 stale connection 错误不应重试")
    void shouldNotRetryNonStaleConnectionError() throws Exception {
      // Given
      IOException timeoutException = new IOException("Read timed out");
      when(execution.execute(any(), any())).thenThrow(timeoutException);

      // When & Then
      assertThatThrownBy(() -> interceptor.intercept(request, body, execution))
          .isSameAs(timeoutException);
      verify(execution, times(1)).execute(any(), any());
    }
  }

  @Nested
  @DisplayName("Stale Connection 重试")
  class StaleConnectionRetryTests {

    @Test
    @DisplayName("检测到 'header parser received no bytes' 错误应重试")
    void shouldRetryOnHeaderParserError() throws Exception {
      // Given
      IOException staleError = new IOException("HTTP/1.1 header parser received no bytes");
      when(execution.execute(any(), any()))
          .thenThrow(staleError)
          .thenReturn(mockResponse);

      // When
      ClientHttpResponse response = interceptor.intercept(request, body, execution);

      // Then
      assertThat(response).isSameAs(mockResponse);
      verify(execution, times(2)).execute(any(), any());
    }

    @Test
    @DisplayName("检测到 'EOF reached while reading' 错误应重试")
    void shouldRetryOnEofError() throws Exception {
      // Given
      IOException staleError = new IOException("EOF reached while reading",
          new EOFException("EOF reached while reading"));
      when(execution.execute(any(), any()))
          .thenThrow(staleError)
          .thenReturn(mockResponse);

      // When
      ClientHttpResponse response = interceptor.intercept(request, body, execution);

      // Then
      assertThat(response).isSameAs(mockResponse);
      verify(execution, times(2)).execute(any(), any());
    }

    @Test
    @DisplayName("检测到 'Connection reset' 错误应重试")
    void shouldRetryOnConnectionReset() throws Exception {
      // Given
      IOException staleError = new IOException("I/O error",
          new SocketException("Connection reset"));
      when(execution.execute(any(), any()))
          .thenThrow(staleError)
          .thenReturn(mockResponse);

      // When
      ClientHttpResponse response = interceptor.intercept(request, body, execution);

      // Then
      assertThat(response).isSameAs(mockResponse);
      verify(execution, times(2)).execute(any(), any());
    }

    @Test
    @DisplayName("检测到 'Broken pipe' 错误应重试")
    void shouldRetryOnBrokenPipe() throws Exception {
      // Given
      IOException staleError = new IOException("Broken pipe");
      when(execution.execute(any(), any()))
          .thenThrow(staleError)
          .thenReturn(mockResponse);

      // When
      ClientHttpResponse response = interceptor.intercept(request, body, execution);

      // Then
      assertThat(response).isSameAs(mockResponse);
      verify(execution, times(2)).execute(any(), any());
    }

    @Test
    @DisplayName("多次重试后成功")
    void shouldSucceedAfterMultipleRetries() throws Exception {
      // Given
      IOException staleError = new IOException("HTTP/1.1 header parser received no bytes");
      when(execution.execute(any(), any()))
          .thenThrow(staleError)
          .thenThrow(staleError)
          .thenReturn(mockResponse);

      // When
      ClientHttpResponse response = interceptor.intercept(request, body, execution);

      // Then
      assertThat(response).isSameAs(mockResponse);
      verify(execution, times(3)).execute(any(), any());  // 1 initial + 2 retries
    }

    @Test
    @DisplayName("达到最大重试次数后应抛出异常")
    void shouldThrowAfterMaxRetries() throws Exception {
      // Given
      IOException staleError = new IOException("HTTP/1.1 header parser received no bytes");
      when(execution.execute(any(), any())).thenThrow(staleError);

      // When & Then
      assertThatThrownBy(() -> interceptor.intercept(request, body, execution))
          .isSameAs(staleError);
      verify(execution, times(3)).execute(any(), any());  // 1 initial + 2 retries
    }
  }

  @Nested
  @DisplayName("Cause 链检测")
  class CauseChainDetectionTests {

    @Test
    @DisplayName("应检测嵌套在 cause 中的 stale connection 错误")
    void shouldDetectStaleConnectionInCauseChain() throws Exception {
      // Given
      IOException rootCause = new IOException("EOF reached while reading");
      IOException wrapperException = new IOException("I/O error on POST request", rootCause);
      when(execution.execute(any(), any()))
          .thenThrow(wrapperException)
          .thenReturn(mockResponse);

      // When
      ClientHttpResponse response = interceptor.intercept(request, body, execution);

      // Then
      assertThat(response).isSameAs(mockResponse);
      verify(execution, times(2)).execute(any(), any());
    }
  }
}
