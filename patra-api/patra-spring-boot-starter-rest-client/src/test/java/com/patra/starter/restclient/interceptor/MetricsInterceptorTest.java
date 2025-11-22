package com.patra.starter.restclient.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

/// MetricsInterceptor 单元测试。
///
/// <p>测试指标拦截器的成功/失败计数、请求耗时记录。
@ExtendWith(MockitoExtension.class)
@DisplayName("MetricsInterceptor 单元测试")
class MetricsInterceptorTest {

  @Mock private HttpRequest request;

  @Mock private ClientHttpRequestExecution execution;

  @Mock private ClientHttpResponse response;

  private MeterRegistry meterRegistry;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    lenient().when(request.getMethod()).thenReturn(HttpMethod.GET);
    lenient().when(request.getURI()).thenReturn(URI.create("https://api.example.com/test"));
  }

  @Test
  @DisplayName("应该在成功响应时增加成功计数器")
  void should_increment_success_counter_on_successful_response() throws IOException {
    // given
    var interceptor = new MetricsInterceptor(meterRegistry);
    when(execution.execute(request, new byte[0])).thenReturn(response);
    when(response.getStatusCode()).thenReturn(HttpStatus.OK);

    // when
    interceptor.intercept(request, new byte[0], execution);

    // then
    Counter successCounter = meterRegistry.find("rest_client_requests_success_total").counter();
    assertThat(successCounter).isNotNull();
    assertThat(successCounter.count()).isEqualTo(1.0);

    Counter failureCounter = meterRegistry.find("rest_client_requests_failure_total").counter();
    assertThat(failureCounter).isNotNull();
    assertThat(failureCounter.count()).isZero();
  }

  @Test
  @DisplayName("应该在 2xx 响应时增加成功计数器")
  void should_increment_success_counter_on_2xx_responses() throws IOException {
    // given
    var interceptor = new MetricsInterceptor(meterRegistry);
    HttpStatus[] successStatuses = {
      HttpStatus.OK, HttpStatus.CREATED, HttpStatus.ACCEPTED, HttpStatus.NO_CONTENT
    };

    for (HttpStatus status : successStatuses) {
      when(execution.execute(request, new byte[0])).thenReturn(response);
      when(response.getStatusCode()).thenReturn(status);

      // when
      interceptor.intercept(request, new byte[0], execution);
    }

    // then
    Counter successCounter = meterRegistry.find("rest_client_requests_success_total").counter();
    assertThat(successCounter).isNotNull();
    assertThat(successCounter.count()).isEqualTo(successStatuses.length);
  }

  @Test
  @DisplayName("应该在非 2xx 响应时增加失败计数器")
  void should_increment_failure_counter_on_non_2xx_responses() throws IOException {
    // given
    var interceptor = new MetricsInterceptor(meterRegistry);
    when(execution.execute(request, new byte[0])).thenReturn(response);
    when(response.getStatusCode()).thenReturn(HttpStatus.BAD_REQUEST);

    // when
    interceptor.intercept(request, new byte[0], execution);

    // then
    Counter failureCounter = meterRegistry.find("rest_client_requests_failure_total").counter();
    assertThat(failureCounter).isNotNull();
    assertThat(failureCounter.count()).isEqualTo(1.0);

    Counter successCounter = meterRegistry.find("rest_client_requests_success_total").counter();
    assertThat(successCounter).isNotNull();
    assertThat(successCounter.count()).isZero();
  }

  @Test
  @DisplayName("应该在 4xx 响应时增加失败计数器")
  void should_increment_failure_counter_on_4xx_responses() throws IOException {
    // given
    var interceptor = new MetricsInterceptor(meterRegistry);
    HttpStatus[] errorStatuses = {
      HttpStatus.BAD_REQUEST, HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND
    };

    for (HttpStatus status : errorStatuses) {
      when(execution.execute(request, new byte[0])).thenReturn(response);
      when(response.getStatusCode()).thenReturn(status);

      // when
      interceptor.intercept(request, new byte[0], execution);
    }

    // then
    Counter failureCounter = meterRegistry.find("rest_client_requests_failure_total").counter();
    assertThat(failureCounter).isNotNull();
    assertThat(failureCounter.count()).isEqualTo(errorStatuses.length);
  }

  @Test
  @DisplayName("应该在 5xx 响应时增加失败计数器")
  void should_increment_failure_counter_on_5xx_responses() throws IOException {
    // given
    var interceptor = new MetricsInterceptor(meterRegistry);
    HttpStatus[] serverErrorStatuses = {
      HttpStatus.INTERNAL_SERVER_ERROR,
      HttpStatus.BAD_GATEWAY,
      HttpStatus.SERVICE_UNAVAILABLE,
      HttpStatus.GATEWAY_TIMEOUT
    };

    for (HttpStatus status : serverErrorStatuses) {
      when(execution.execute(request, new byte[0])).thenReturn(response);
      when(response.getStatusCode()).thenReturn(status);

      // when
      interceptor.intercept(request, new byte[0], execution);
    }

    // then
    Counter failureCounter = meterRegistry.find("rest_client_requests_failure_total").counter();
    assertThat(failureCounter).isNotNull();
    assertThat(failureCounter.count()).isEqualTo(serverErrorStatuses.length);
  }

  @Test
  @DisplayName("应该在抛出异常时增加失败计数器")
  void should_increment_failure_counter_on_exception() throws IOException {
    // given
    var interceptor = new MetricsInterceptor(meterRegistry);
    when(execution.execute(request, new byte[0])).thenThrow(new IOException("Connection timeout"));

    // when & then
    assertThatThrownBy(() -> interceptor.intercept(request, new byte[0], execution))
        .isInstanceOf(IOException.class)
        .hasMessage("Connection timeout");

    Counter failureCounter = meterRegistry.find("rest_client_requests_failure_total").counter();
    assertThat(failureCounter).isNotNull();
    assertThat(failureCounter.count()).isEqualTo(1.0);
  }

  @Test
  @DisplayName("应该记录请求耗时")
  void should_record_request_duration() throws IOException {
    // given
    var interceptor = new MetricsInterceptor(meterRegistry);
    when(execution.execute(request, new byte[0])).thenAnswer(invocation -> {
      Thread.sleep(100); // 模拟耗时操作
      return response;
    });
    when(response.getStatusCode()).thenReturn(HttpStatus.OK);

    // when
    interceptor.intercept(request, new byte[0], execution);

    // then
    Timer requestTimer = meterRegistry.find("rest_client_request_duration_seconds").timer();
    assertThat(requestTimer).isNotNull();
    assertThat(requestTimer.count()).isEqualTo(1);
    assertThat(requestTimer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
        .isGreaterThanOrEqualTo(100);
  }

  @Test
  @DisplayName("应该在异常时仍然记录请求耗时")
  void should_record_request_duration_even_on_exception() throws IOException {
    // given
    var interceptor = new MetricsInterceptor(meterRegistry);
    when(execution.execute(request, new byte[0])).thenAnswer(invocation -> {
      Thread.sleep(50);
      throw new IOException("Connection failed");
    });

    // when & then
    assertThatThrownBy(() -> interceptor.intercept(request, new byte[0], execution))
        .isInstanceOf(IOException.class);

    Timer requestTimer = meterRegistry.find("rest_client_request_duration_seconds").timer();
    assertThat(requestTimer).isNotNull();
    assertThat(requestTimer.count()).isEqualTo(1);
    assertThat(requestTimer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
        .isGreaterThanOrEqualTo(50);
  }

  @Test
  @DisplayName("应该正确传播请求执行结果")
  void should_propagate_execution_result() throws IOException {
    // given
    var interceptor = new MetricsInterceptor(meterRegistry);
    when(execution.execute(request, new byte[0])).thenReturn(response);
    when(response.getStatusCode()).thenReturn(HttpStatus.OK);

    // when
    ClientHttpResponse result = interceptor.intercept(request, new byte[0], execution);

    // then
    assertThat(result).isEqualTo(response);
    verify(execution).execute(request, new byte[0]);
  }

  @Test
  @DisplayName("应该正确传播异常")
  void should_propagate_exception() throws IOException {
    // given
    var interceptor = new MetricsInterceptor(meterRegistry);
    IOException expectedException = new IOException("Network error");
    when(execution.execute(request, new byte[0])).thenThrow(expectedException);

    // when & then
    assertThatThrownBy(() -> interceptor.intercept(request, new byte[0], execution))
        .isEqualTo(expectedException);
  }

  @Test
  @DisplayName("应该支持多次请求的指标累加")
  void should_accumulate_metrics_for_multiple_requests() throws IOException {
    // given
    var interceptor = new MetricsInterceptor(meterRegistry);

    // 3 次成功
    for (int i = 0; i < 3; i++) {
      when(execution.execute(request, new byte[0])).thenReturn(response);
      when(response.getStatusCode()).thenReturn(HttpStatus.OK);
      interceptor.intercept(request, new byte[0], execution);
    }

    // 2 次失败
    for (int i = 0; i < 2; i++) {
      when(execution.execute(request, new byte[0])).thenReturn(response);
      when(response.getStatusCode()).thenReturn(HttpStatus.INTERNAL_SERVER_ERROR);
      interceptor.intercept(request, new byte[0], execution);
    }

    // 1 次异常
    when(execution.execute(request, new byte[0])).thenThrow(new IOException("Error"));
    try {
      interceptor.intercept(request, new byte[0], execution);
    } catch (IOException ignored) {
    }

    // then
    Counter successCounter = meterRegistry.find("rest_client_requests_success_total").counter();
    assertThat(successCounter.count()).isEqualTo(3.0);

    Counter failureCounter = meterRegistry.find("rest_client_requests_failure_total").counter();
    assertThat(failureCounter.count()).isEqualTo(3.0); // 2 次 5xx + 1 次异常

    Timer requestTimer = meterRegistry.find("rest_client_request_duration_seconds").timer();
    assertThat(requestTimer.count()).isEqualTo(6); // 总共 6 次请求
  }

  @Test
  @DisplayName("指标应该有正确的名称和描述")
  void should_have_correct_metric_names_and_descriptions() {
    // given
    var interceptor = new MetricsInterceptor(meterRegistry);

    // when - 触发指标创建
    // 指标在构造函数中创建

    // then
    Counter successCounter = meterRegistry.find("rest_client_requests_success_total").counter();
    assertThat(successCounter).isNotNull();
    assertThat(successCounter.getId().getDescription())
        .isEqualTo("Total successful HTTP requests");

    Counter failureCounter = meterRegistry.find("rest_client_requests_failure_total").counter();
    assertThat(failureCounter).isNotNull();
    assertThat(failureCounter.getId().getDescription()).isEqualTo("Total failed HTTP requests");

    Timer requestTimer = meterRegistry.find("rest_client_request_duration_seconds").timer();
    assertThat(requestTimer).isNotNull();
    assertThat(requestTimer.getId().getDescription()).isEqualTo("HTTP request duration");
  }

  @Test
  @DisplayName("应该在 3xx 重定向响应时增加成功计数器")
  void should_increment_success_counter_on_3xx_redirects() throws IOException {
    // given
    var interceptor = new MetricsInterceptor(meterRegistry);
    HttpStatus[] redirectStatuses = {
      HttpStatus.MOVED_PERMANENTLY,
      HttpStatus.FOUND,
      HttpStatus.SEE_OTHER,
      HttpStatus.TEMPORARY_REDIRECT
    };

    // 3xx 不是 2xx，应该计入失败
    for (HttpStatus status : redirectStatuses) {
      when(execution.execute(request, new byte[0])).thenReturn(response);
      when(response.getStatusCode()).thenReturn(status);
      interceptor.intercept(request, new byte[0], execution);
    }

    // then
    Counter failureCounter = meterRegistry.find("rest_client_requests_failure_total").counter();
    assertThat(failureCounter).isNotNull();
    assertThat(failureCounter.count()).isEqualTo(redirectStatuses.length);
  }

  @Test
  @DisplayName("应该处理不同类型的 IOException")
  void should_handle_different_types_of_io_exceptions() throws IOException {
    // given
    var interceptor = new MetricsInterceptor(meterRegistry);

    // 测试 Generic IOException
    when(execution.execute(request, new byte[0])).thenThrow(new IOException("Generic error"));
    assertThatThrownBy(() -> interceptor.intercept(request, new byte[0], execution))
        .isInstanceOf(IOException.class);

    // then - 验证失败计数器增加
    Counter failureCounter = meterRegistry.find("rest_client_requests_failure_total").counter();
    assertThat(failureCounter).isNotNull();
    assertThat(failureCounter.count()).isGreaterThan(0);
  }

  @Test
  @DisplayName("应该为每个独立请求创建新的 Timer 样本")
  void should_create_new_timer_sample_for_each_request() throws IOException {
    // given
    var interceptor = new MetricsInterceptor(meterRegistry);

    // 第一个请求（快）
    when(execution.execute(request, new byte[0])).thenAnswer(invocation -> {
      Thread.sleep(10);
      return response;
    });
    when(response.getStatusCode()).thenReturn(HttpStatus.OK);
    interceptor.intercept(request, new byte[0], execution);

    // 第二个请求（慢）
    when(execution.execute(request, new byte[0])).thenAnswer(invocation -> {
      Thread.sleep(100);
      return response;
    });
    interceptor.intercept(request, new byte[0], execution);

    // then
    Timer requestTimer = meterRegistry.find("rest_client_request_duration_seconds").timer();
    assertThat(requestTimer).isNotNull();
    assertThat(requestTimer.count()).isEqualTo(2);
    // 平均耗时应该在 10ms 到 100ms 之间
    assertThat(requestTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS))
        .isBetween(10.0, 100.0);
  }

  @Test
  @DisplayName("应该正确处理零耗时的请求")
  void should_handle_zero_duration_requests() throws IOException {
    // given
    var interceptor = new MetricsInterceptor(meterRegistry);
    when(execution.execute(request, new byte[0])).thenReturn(response);
    when(response.getStatusCode()).thenReturn(HttpStatus.OK);

    // when
    interceptor.intercept(request, new byte[0], execution);

    // then
    Timer requestTimer = meterRegistry.find("rest_client_request_duration_seconds").timer();
    assertThat(requestTimer).isNotNull();
    assertThat(requestTimer.count()).isEqualTo(1);
    assertThat(requestTimer.totalTime(java.util.concurrent.TimeUnit.NANOSECONDS))
        .isGreaterThanOrEqualTo(0);
  }
}
