package com.patra.starter.restclient.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/// LoggingInterceptor 单元测试。
///
/// <p>测试日志拦截器的请求/响应日志记录、配置项和日志级别。
@ExtendWith(MockitoExtension.class)
@DisplayName("LoggingInterceptor 单元测试")
class LoggingInterceptorTest {

  @Mock private HttpRequest request;

  @Mock private ClientHttpRequestExecution execution;

  @Mock private ClientHttpResponse response;

  private ListAppender<ILoggingEvent> listAppender;

  private Logger logger;

  @BeforeEach
  void setUp() {
    // 配置 Logback 日志捕获
    logger = (Logger) LoggerFactory.getLogger(LoggingInterceptor.class);
    listAppender = new ListAppender<>();
    listAppender.start();
    logger.addAppender(listAppender);
    logger.setLevel(Level.DEBUG);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(listAppender);
  }

  @Test
  @DisplayName("应该记录请求方法和 URI")
  void should_log_request_method_and_uri() throws IOException {
    // given
    var interceptor = new LoggingInterceptor(false, false);
    when(request.getMethod()).thenReturn(HttpMethod.GET);
    when(request.getURI()).thenReturn(URI.create("https://api.example.com/test"));
    when(execution.execute(request, new byte[0])).thenReturn(response);
    when(response.getStatusCode()).thenReturn(HttpStatus.OK);

    // when
    interceptor.intercept(request, new byte[0], execution);

    // then
    var logs = listAppender.list;
    assertThat(logs).hasSize(2); // 请求日志 + 响应日志

    var requestLog = logs.get(0);
    assertThat(requestLog.getFormattedMessage())
        .contains("HTTP GET https://api.example.com/test");
  }

  @Test
  @DisplayName("应该记录响应状态码和耗时")
  void should_log_response_status_and_duration() throws IOException {
    // given
    var interceptor = new LoggingInterceptor(false, false);
    when(request.getMethod()).thenReturn(HttpMethod.POST);
    when(request.getURI()).thenReturn(URI.create("https://api.example.com/create"));
    when(execution.execute(request, new byte[0])).thenReturn(response);
    when(response.getStatusCode()).thenReturn(HttpStatus.CREATED);

    // when
    interceptor.intercept(request, new byte[0], execution);

    // then
    var logs = listAppender.list;
    var responseLog = logs.get(1);
    assertThat(responseLog.getFormattedMessage())
        .contains("HTTP POST https://api.example.com/create")
        .contains("201")
        .contains("ms");
  }

  @Test
  @DisplayName("当 logHeaders=true 时应该记录请求 Headers")
  void should_log_request_headers_when_enabled() throws IOException {
    // given
    var interceptor = new LoggingInterceptor(true, false);
    HttpHeaders headers = new HttpHeaders();
    headers.add("Content-Type", "application/json");
    headers.add("User-Agent", "Patra-RestClient/1.0");

    when(request.getMethod()).thenReturn(HttpMethod.POST);
    when(request.getURI()).thenReturn(URI.create("https://api.example.com/test"));
    when(request.getHeaders()).thenReturn(headers);
    when(execution.execute(request, new byte[0])).thenReturn(response);
    when(response.getStatusCode()).thenReturn(HttpStatus.OK);

    // when
    interceptor.intercept(request, new byte[0], execution);

    // then
    var logs = listAppender.list;
    var headerLog = logs.stream()
        .filter(log -> log.getFormattedMessage().contains("Headers:"))
        .findFirst();
    assertThat(headerLog).isPresent();
    assertThat(headerLog.get().getFormattedMessage())
        .contains("Content-Type")
        .contains("User-Agent");
  }

  @Test
  @DisplayName("当 logHeaders=false 时不应该记录请求 Headers")
  void should_not_log_request_headers_when_disabled() throws IOException {
    // given
    var interceptor = new LoggingInterceptor(false, false);
    HttpHeaders headers = new HttpHeaders();
    headers.add("Authorization", "Bearer secret-token");

    lenient().when(request.getMethod()).thenReturn(HttpMethod.GET);
    lenient().when(request.getURI()).thenReturn(URI.create("https://api.example.com/test"));
    lenient().when(request.getHeaders()).thenReturn(headers);
    lenient().when(execution.execute(request, new byte[0])).thenReturn(response);
    lenient().when(response.getStatusCode()).thenReturn(HttpStatus.OK);

    // when
    interceptor.intercept(request, new byte[0], execution);

    // then
    var logs = listAppender.list;
    var headerLog = logs.stream()
        .anyMatch(log -> log.getFormattedMessage().contains("Headers:"));
    assertThat(headerLog).isFalse();
  }

  @Test
  @DisplayName("当 logBody=true 且有请求体时应该记录 Body")
  void should_log_request_body_when_enabled_and_has_body() throws IOException {
    // given
    var interceptor = new LoggingInterceptor(false, true);
    String requestBody = "{\"name\":\"test\",\"value\":\"data\"}";
    byte[] bodyBytes = requestBody.getBytes(StandardCharsets.UTF_8);

    when(request.getMethod()).thenReturn(HttpMethod.POST);
    when(request.getURI()).thenReturn(URI.create("https://api.example.com/test"));
    when(execution.execute(request, bodyBytes)).thenReturn(response);
    when(response.getStatusCode()).thenReturn(HttpStatus.OK);

    // when
    interceptor.intercept(request, bodyBytes, execution);

    // then
    var logs = listAppender.list;
    var bodyLog = logs.stream()
        .filter(log -> log.getFormattedMessage().contains("Body:"))
        .findFirst();
    assertThat(bodyLog).isPresent();
    assertThat(bodyLog.get().getFormattedMessage()).contains(requestBody);
  }

  @Test
  @DisplayName("当 logBody=true 但无请求体时不应该记录 Body")
  void should_not_log_request_body_when_empty() throws IOException {
    // given
    var interceptor = new LoggingInterceptor(false, true);

    when(request.getMethod()).thenReturn(HttpMethod.GET);
    when(request.getURI()).thenReturn(URI.create("https://api.example.com/test"));
    when(execution.execute(request, new byte[0])).thenReturn(response);
    when(response.getStatusCode()).thenReturn(HttpStatus.OK);

    // when
    interceptor.intercept(request, new byte[0], execution);

    // then
    var logs = listAppender.list;
    var bodyLog = logs.stream()
        .anyMatch(log -> log.getFormattedMessage().contains("Body:"));
    assertThat(bodyLog).isFalse();
  }

  @Test
  @DisplayName("当 logBody=false 时不应该记录请求 Body")
  void should_not_log_request_body_when_disabled() throws IOException {
    // given
    var interceptor = new LoggingInterceptor(false, false);
    String requestBody = "{\"password\":\"secret123\"}";
    byte[] bodyBytes = requestBody.getBytes(StandardCharsets.UTF_8);

    when(request.getMethod()).thenReturn(HttpMethod.POST);
    when(request.getURI()).thenReturn(URI.create("https://api.example.com/login"));
    when(execution.execute(request, bodyBytes)).thenReturn(response);
    when(response.getStatusCode()).thenReturn(HttpStatus.OK);

    // when
    interceptor.intercept(request, bodyBytes, execution);

    // then
    var logs = listAppender.list;
    var bodyLog = logs.stream()
        .anyMatch(log -> log.getFormattedMessage().contains("password"));
    assertThat(bodyLog).isFalse();
  }

  @Test
  @DisplayName("当日志级别不是 DEBUG 时不应该记录日志")
  void should_not_log_when_debug_level_disabled() throws IOException {
    // given
    logger.setLevel(Level.INFO); // 设置为 INFO 级别
    var interceptor = new LoggingInterceptor(true, true);

    lenient().when(request.getMethod()).thenReturn(HttpMethod.GET);
    lenient().when(request.getURI()).thenReturn(URI.create("https://api.example.com/test"));
    lenient().when(execution.execute(request, new byte[0])).thenReturn(response);
    lenient().when(response.getStatusCode()).thenReturn(HttpStatus.OK);

    // when
    interceptor.intercept(request, new byte[0], execution);

    // then
    assertThat(listAppender.list).isEmpty();
  }

  @Test
  @DisplayName("应该正确传播请求执行结果")
  void should_propagate_execution_result() throws IOException {
    // given
    var interceptor = new LoggingInterceptor(false, false);
    when(request.getMethod()).thenReturn(HttpMethod.GET);
    when(request.getURI()).thenReturn(URI.create("https://api.example.com/test"));
    when(execution.execute(request, new byte[0])).thenReturn(response);
    when(response.getStatusCode()).thenReturn(HttpStatus.OK);

    // when
    ClientHttpResponse result = interceptor.intercept(request, new byte[0], execution);

    // then
    assertThat(result).isEqualTo(response);
    verify(execution).execute(request, new byte[0]);
  }

  @Test
  @DisplayName("应该记录请求耗时")
  void should_log_request_duration() throws IOException {
    // given
    var interceptor = new LoggingInterceptor(false, false);
    when(request.getMethod()).thenReturn(HttpMethod.GET);
    when(request.getURI()).thenReturn(URI.create("https://api.example.com/slow"));
    when(execution.execute(request, new byte[0])).thenAnswer(invocation -> {
      Thread.sleep(100); // 模拟慢请求
      return response;
    });
    when(response.getStatusCode()).thenReturn(HttpStatus.OK);

    // when
    interceptor.intercept(request, new byte[0], execution);

    // then
    var logs = listAppender.list;
    var responseLog = logs.get(1);
    assertThat(responseLog.getFormattedMessage())
        .matches(".*\\d+ ms\\)"); // 应该包含耗时（毫秒）
  }

  @Test
  @DisplayName("当获取响应状态码失败时应该记录警告")
  void should_log_warning_when_failed_to_get_status_code() throws IOException {
    // given
    var interceptor = new LoggingInterceptor(false, false);
    when(request.getMethod()).thenReturn(HttpMethod.GET);
    when(request.getURI()).thenReturn(URI.create("https://api.example.com/test"));
    when(execution.execute(request, new byte[0])).thenReturn(response);
    when(response.getStatusCode()).thenThrow(new IOException("Connection reset"));

    // when
    interceptor.intercept(request, new byte[0], execution);

    // then
    var logs = listAppender.list;
    var warnLog = logs.stream()
        .filter(log -> log.getLevel() == Level.WARN)
        .findFirst();
    assertThat(warnLog).isPresent();
    assertThat(warnLog.get().getFormattedMessage())
        .contains("Failed to log response status");
  }

  @Test
  @DisplayName("应该正确处理不同的 HTTP 方法")
  void should_handle_different_http_methods() throws IOException {
    // given
    var interceptor = new LoggingInterceptor(false, false);
    HttpMethod[] methods = {
      HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT,
      HttpMethod.DELETE, HttpMethod.PATCH, HttpMethod.HEAD
    };

    for (HttpMethod method : methods) {
      listAppender.list.clear();
      when(request.getMethod()).thenReturn(method);
      when(request.getURI()).thenReturn(URI.create("https://api.example.com/test"));
      when(execution.execute(request, new byte[0])).thenReturn(response);
      when(response.getStatusCode()).thenReturn(HttpStatus.OK);

      // when
      interceptor.intercept(request, new byte[0], execution);

      // then
      var logs = listAppender.list;
      assertThat(logs).isNotEmpty();
      assertThat(logs.get(0).getFormattedMessage()).contains("HTTP " + method.name());
    }
  }

  @Test
  @DisplayName("应该正确处理不同的 HTTP 状态码")
  void should_handle_different_http_status_codes() throws IOException {
    // given
    var interceptor = new LoggingInterceptor(false, false);
    HttpStatusCode[] statusCodes = {
      HttpStatus.OK, HttpStatus.CREATED, HttpStatus.BAD_REQUEST,
      HttpStatus.NOT_FOUND, HttpStatus.INTERNAL_SERVER_ERROR
    };

    for (HttpStatusCode statusCode : statusCodes) {
      listAppender.list.clear();
      when(request.getMethod()).thenReturn(HttpMethod.GET);
      when(request.getURI()).thenReturn(URI.create("https://api.example.com/test"));
      when(execution.execute(request, new byte[0])).thenReturn(response);
      when(response.getStatusCode()).thenReturn(statusCode);

      // when
      interceptor.intercept(request, new byte[0], execution);

      // then
      var logs = listAppender.list;
      var responseLog = logs.get(1);
      assertThat(responseLog.getFormattedMessage()).contains(String.valueOf(statusCode.value()));
    }
  }

  @Test
  @DisplayName("应该正确处理包含特殊字符的请求体")
  void should_handle_request_body_with_special_characters() throws IOException {
    // given
    var interceptor = new LoggingInterceptor(false, true);
    String requestBody = "{\"text\":\"Hello\\nWorld\\t测试\",\"emoji\":\"😀\"}";
    byte[] bodyBytes = requestBody.getBytes(StandardCharsets.UTF_8);

    when(request.getMethod()).thenReturn(HttpMethod.POST);
    when(request.getURI()).thenReturn(URI.create("https://api.example.com/test"));
    when(execution.execute(request, bodyBytes)).thenReturn(response);
    when(response.getStatusCode()).thenReturn(HttpStatus.OK);

    // when
    assertThatCode(() -> interceptor.intercept(request, bodyBytes, execution))
        .doesNotThrowAnyException();

    // then
    var logs = listAppender.list;
    var bodyLog = logs.stream()
        .filter(log -> log.getFormattedMessage().contains("Body:"))
        .findFirst();
    assertThat(bodyLog).isPresent();
  }

  @Test
  @DisplayName("应该正确处理空 URI")
  void should_handle_null_uri_gracefully() throws IOException {
    // given
    var interceptor = new LoggingInterceptor(false, false);
    lenient().when(request.getMethod()).thenReturn(HttpMethod.GET);
    lenient().when(request.getURI()).thenReturn(null);
    lenient().when(execution.execute(request, new byte[0])).thenReturn(response);
    lenient().when(response.getStatusCode()).thenReturn(HttpStatus.OK);

    // when & then
    assertThatCode(() -> interceptor.intercept(request, new byte[0], execution))
        .doesNotThrowAnyException();
  }
}
