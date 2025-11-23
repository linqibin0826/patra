package com.patra.starter.restclient.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import org.apache.skywalking.apm.toolkit.trace.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

/// TracingInterceptor 单元测试。
///
/// <p>测试追踪拦截器的 TraceID 提取、Header 传播和多 Header 名称支持。
@ExtendWith(MockitoExtension.class)
@DisplayName("TracingInterceptor 单元测试")
class TracingInterceptorTest {

  @Mock private HttpRequest request;

  @Mock private ClientHttpRequestExecution execution;

  @Mock private ClientHttpResponse response;

  private HttpHeaders headers;

  @BeforeEach
  void setUp() {
    headers = new HttpHeaders();
    lenient().when(request.getHeaders()).thenReturn(headers);
    lenient().when(request.getMethod()).thenReturn(HttpMethod.GET);
    lenient().when(request.getURI()).thenReturn(URI.create("https://api.example.com/test"));
    MDC.clear();
  }

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  @DisplayName("应该从 SkyWalking 提取 TraceID 并传播")
  void should_extract_trace_id_from_skywalking_and_propagate() throws IOException {
    // given
    String swTraceId = "sw-trace-123456";
    var interceptor = new TracingInterceptor(List.of("X-Trace-ID", "X-B3-TraceId"));

    try (MockedStatic<TraceContext> mockedTraceContext = mockStatic(TraceContext.class)) {
      mockedTraceContext.when(TraceContext::traceId).thenReturn(swTraceId);
      when(execution.execute(request, new byte[0])).thenReturn(response);

      // when
      interceptor.intercept(request, new byte[0], execution);

      // then
      assertThat(headers.get("X-Trace-ID")).containsExactly(swTraceId);
      assertThat(headers.get("X-B3-TraceId")).containsExactly(swTraceId);
      verify(execution).execute(request, new byte[0]);
    }
  }

  @Test
  @DisplayName("当 SkyWalking 不可用时应该从 MDC 提取 TraceID")
  void should_extract_trace_id_from_mdc_when_skywalking_unavailable() throws IOException {
    // given
    String mdcTraceId = "mdc-trace-789";
    MDC.put("traceId", mdcTraceId);
    var interceptor = new TracingInterceptor(List.of("X-Trace-ID"));

    try (MockedStatic<TraceContext> mockedTraceContext = mockStatic(TraceContext.class)) {
      mockedTraceContext
          .when(TraceContext::traceId)
          .thenThrow(new RuntimeException("SkyWalking not available"));
      when(execution.execute(request, new byte[0])).thenReturn(response);

      // when
      interceptor.intercept(request, new byte[0], execution);

      // then
      assertThat(headers.get("X-Trace-ID")).containsExactly(mdcTraceId);
      verify(execution).execute(request, new byte[0]);
    }
  }

  @Test
  @DisplayName("当 SkyWalking 返回空 TraceID 时应该降级到 MDC")
  void should_fallback_to_mdc_when_skywalking_returns_empty() throws IOException {
    // given
    String mdcTraceId = "mdc-trace-fallback";
    MDC.put("traceId", mdcTraceId);
    var interceptor = new TracingInterceptor(List.of("X-Trace-ID"));

    try (MockedStatic<TraceContext> mockedTraceContext = mockStatic(TraceContext.class)) {
      mockedTraceContext.when(TraceContext::traceId).thenReturn(""); // 空字符串
      when(execution.execute(request, new byte[0])).thenReturn(response);

      // when
      interceptor.intercept(request, new byte[0], execution);

      // then
      assertThat(headers.get("X-Trace-ID")).containsExactly(mdcTraceId);
    }
  }

  @Test
  @DisplayName("当 SkyWalking 返回 null 时应该降级到 MDC")
  void should_fallback_to_mdc_when_skywalking_returns_null() throws IOException {
    // given
    String mdcTraceId = "mdc-trace-null-fallback";
    MDC.put("traceId", mdcTraceId);
    var interceptor = new TracingInterceptor(List.of("X-Trace-ID"));

    try (MockedStatic<TraceContext> mockedTraceContext = mockStatic(TraceContext.class)) {
      mockedTraceContext.when(TraceContext::traceId).thenReturn(null);
      when(execution.execute(request, new byte[0])).thenReturn(response);

      // when
      interceptor.intercept(request, new byte[0], execution);

      // then
      assertThat(headers.get("X-Trace-ID")).containsExactly(mdcTraceId);
    }
  }

  @Test
  @DisplayName("当 TraceID 不存在时不应该添加 Header")
  void should_not_add_headers_when_trace_id_not_exists() throws IOException {
    // given
    var interceptor = new TracingInterceptor(List.of("X-Trace-ID"));

    try (MockedStatic<TraceContext> mockedTraceContext = mockStatic(TraceContext.class)) {
      mockedTraceContext.when(TraceContext::traceId).thenReturn(null);
      when(execution.execute(request, new byte[0])).thenReturn(response);

      // when
      interceptor.intercept(request, new byte[0], execution);

      // then
      assertThat(headers.get("X-Trace-ID")).isNull();
      verify(execution).execute(request, new byte[0]);
    }
  }

  @Test
  @DisplayName("应该支持多个 Header 名称")
  void should_support_multiple_header_names() throws IOException {
    // given
    String traceId = "multi-header-trace";
    var headerNames = List.of("X-Trace-ID", "X-B3-TraceId", "X-Request-ID", "X-Correlation-ID");
    var interceptor = new TracingInterceptor(headerNames);

    try (MockedStatic<TraceContext> mockedTraceContext = mockStatic(TraceContext.class)) {
      mockedTraceContext.when(TraceContext::traceId).thenReturn(traceId);
      when(execution.execute(request, new byte[0])).thenReturn(response);

      // when
      interceptor.intercept(request, new byte[0], execution);

      // then
      for (String headerName : headerNames) {
        assertThat(headers.get(headerName)).containsExactly(traceId);
      }
    }
  }

  @Test
  @DisplayName("应该处理空的 Header 名称列表")
  void should_handle_empty_header_names_list() throws IOException {
    // given
    String traceId = "empty-list-trace";
    var interceptor = new TracingInterceptor(List.of());

    try (MockedStatic<TraceContext> mockedTraceContext = mockStatic(TraceContext.class)) {
      mockedTraceContext.when(TraceContext::traceId).thenReturn(traceId);
      when(execution.execute(request, new byte[0])).thenReturn(response);

      // when
      interceptor.intercept(request, new byte[0], execution);

      // then
      assertThat(headers).isEmpty();
      verify(execution).execute(request, new byte[0]);
    }
  }

  @Test
  @DisplayName("应该处理 null 的 Header 名称列表")
  void should_handle_null_header_names_list() throws IOException {
    // given
    String traceId = "null-list-trace";
    var interceptor = new TracingInterceptor(null);

    try (MockedStatic<TraceContext> mockedTraceContext = mockStatic(TraceContext.class)) {
      mockedTraceContext.when(TraceContext::traceId).thenReturn(traceId);
      when(execution.execute(request, new byte[0])).thenReturn(response);

      // when
      interceptor.intercept(request, new byte[0], execution);

      // then
      assertThat(headers).isEmpty();
      verify(execution).execute(request, new byte[0]);
    }
  }

  @Test
  @DisplayName("应该正确传播请求执行结果")
  void should_propagate_execution_result() throws IOException {
    // given
    var interceptor = new TracingInterceptor(List.of("X-Trace-ID"));
    when(execution.execute(request, new byte[0])).thenReturn(response);

    try (MockedStatic<TraceContext> mockedTraceContext = mockStatic(TraceContext.class)) {
      mockedTraceContext.when(TraceContext::traceId).thenReturn("trace-123");

      // when
      ClientHttpResponse result = interceptor.intercept(request, new byte[0], execution);

      // then
      assertThat(result).isEqualTo(response);
      verify(execution).execute(request, new byte[0]);
    }
  }

  @Test
  @DisplayName("SkyWalking 优先级应该高于 MDC")
  void should_prioritize_skywalking_over_mdc() throws IOException {
    // given
    String swTraceId = "sw-trace-priority";
    String mdcTraceId = "mdc-trace-low";
    MDC.put("traceId", mdcTraceId);
    var interceptor = new TracingInterceptor(List.of("X-Trace-ID"));

    try (MockedStatic<TraceContext> mockedTraceContext = mockStatic(TraceContext.class)) {
      mockedTraceContext.when(TraceContext::traceId).thenReturn(swTraceId);
      when(execution.execute(request, new byte[0])).thenReturn(response);

      // when
      interceptor.intercept(request, new byte[0], execution);

      // then
      assertThat(headers.get("X-Trace-ID")).containsExactly(swTraceId);
      assertThat(headers.get("X-Trace-ID")).doesNotContain(mdcTraceId);
    }
  }

  @Test
  @DisplayName("应该处理 MDC 中不存在 traceId 的情况")
  void should_handle_missing_trace_id_in_mdc() throws IOException {
    // given
    var interceptor = new TracingInterceptor(List.of("X-Trace-ID"));

    try (MockedStatic<TraceContext> mockedTraceContext = mockStatic(TraceContext.class)) {
      mockedTraceContext.when(TraceContext::traceId).thenReturn(null);
      // MDC 中没有 traceId
      when(execution.execute(request, new byte[0])).thenReturn(response);

      // when
      interceptor.intercept(request, new byte[0], execution);

      // then
      assertThat(headers.get("X-Trace-ID")).isNull();
    }
  }

  @Test
  @DisplayName("应该处理空字符串的 TraceID")
  void should_handle_empty_string_trace_id() throws IOException {
    // given
    var interceptor = new TracingInterceptor(List.of("X-Trace-ID"));

    try (MockedStatic<TraceContext> mockedTraceContext = mockStatic(TraceContext.class)) {
      mockedTraceContext.when(TraceContext::traceId).thenReturn("");
      MDC.put("traceId", "");
      when(execution.execute(request, new byte[0])).thenReturn(response);

      // when
      interceptor.intercept(request, new byte[0], execution);

      // then
      assertThat(headers.get("X-Trace-ID")).isNull();
    }
  }

  @Test
  @DisplayName("应该在已有 Headers 的请求中添加 TraceID")
  void should_add_trace_id_to_existing_headers() throws IOException {
    // given
    String traceId = "existing-headers-trace";
    headers.add("Content-Type", "application/json");
    headers.add("User-Agent", "Test-Agent");
    var interceptor = new TracingInterceptor(List.of("X-Trace-ID"));

    try (MockedStatic<TraceContext> mockedTraceContext = mockStatic(TraceContext.class)) {
      mockedTraceContext.when(TraceContext::traceId).thenReturn(traceId);
      when(execution.execute(request, new byte[0])).thenReturn(response);

      // when
      interceptor.intercept(request, new byte[0], execution);

      // then
      assertThat(headers.get("X-Trace-ID")).containsExactly(traceId);
      assertThat(headers.get("Content-Type")).containsExactly("application/json");
      assertThat(headers.get("User-Agent")).containsExactly("Test-Agent");
    }
  }

  @Test
  @DisplayName("应该处理特殊字符的 TraceID")
  void should_handle_special_characters_in_trace_id() throws IOException {
    // given
    String traceId = "trace-with-special-chars-!@#$%^&*()";
    var interceptor = new TracingInterceptor(List.of("X-Trace-ID"));

    try (MockedStatic<TraceContext> mockedTraceContext = mockStatic(TraceContext.class)) {
      mockedTraceContext.when(TraceContext::traceId).thenReturn(traceId);
      when(execution.execute(request, new byte[0])).thenReturn(response);

      // when
      interceptor.intercept(request, new byte[0], execution);

      // then
      assertThat(headers.get("X-Trace-ID")).containsExactly(traceId);
    }
  }

  @Test
  @DisplayName("应该处理长 TraceID")
  void should_handle_long_trace_id() throws IOException {
    // given
    String longTraceId = "a".repeat(500); // 500 字符的长 TraceID
    var interceptor = new TracingInterceptor(List.of("X-Trace-ID"));

    try (MockedStatic<TraceContext> mockedTraceContext = mockStatic(TraceContext.class)) {
      mockedTraceContext.when(TraceContext::traceId).thenReturn(longTraceId);
      when(execution.execute(request, new byte[0])).thenReturn(response);

      // when
      interceptor.intercept(request, new byte[0], execution);

      // then
      assertThat(headers.get("X-Trace-ID")).containsExactly(longTraceId);
    }
  }

  @Test
  @DisplayName("应该为每个请求独立传播 TraceID")
  void should_propagate_trace_id_independently_for_each_request() throws IOException {
    // given
    var interceptor = new TracingInterceptor(List.of("X-Trace-ID"));

    try (MockedStatic<TraceContext> mockedTraceContext = mockStatic(TraceContext.class)) {
      // 第一个请求
      mockedTraceContext.when(TraceContext::traceId).thenReturn("trace-1");
      HttpHeaders headers1 = new HttpHeaders();
      HttpRequest request1 = mock(HttpRequest.class);
      lenient().when(request1.getHeaders()).thenReturn(headers1);
      lenient().when(request1.getMethod()).thenReturn(HttpMethod.GET);
      lenient().when(request1.getURI()).thenReturn(URI.create("https://api.example.com/test1"));
      lenient().when(execution.execute(request1, new byte[0])).thenReturn(response);

      interceptor.intercept(request1, new byte[0], execution);
      assertThat(headers1.get("X-Trace-ID")).containsExactly("trace-1");

      // 第二个请求
      mockedTraceContext.when(TraceContext::traceId).thenReturn("trace-2");
      HttpHeaders headers2 = new HttpHeaders();
      HttpRequest request2 = mock(HttpRequest.class);
      lenient().when(request2.getHeaders()).thenReturn(headers2);
      lenient().when(request2.getMethod()).thenReturn(HttpMethod.GET);
      lenient().when(request2.getURI()).thenReturn(URI.create("https://api.example.com/test2"));
      lenient().when(execution.execute(request2, new byte[0])).thenReturn(response);

      interceptor.intercept(request2, new byte[0], execution);
      assertThat(headers2.get("X-Trace-ID")).containsExactly("trace-2");
    }
  }
}
