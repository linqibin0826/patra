package com.patra.starter.web.error.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.common.error.codes.ErrorCodeLike;
import com.patra.common.error.problem.ErrorKeys;
import com.patra.starter.core.error.config.ErrorProperties;
import com.patra.starter.core.error.model.ErrorResolution;
import com.patra.starter.core.error.spi.ProblemFieldContributor;
import com.patra.starter.core.error.spi.TraceProvider;
import com.patra.starter.web.error.config.WebErrorProperties;
import com.patra.starter.web.error.spi.WebProblemFieldContributor;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ProblemDetail;

/** ProblemDetailBuilder 单元测试。 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProblemDetailBuilder 单元测试")
class ProblemDetailBuilderTest {

  @Mock private ErrorProperties errorProperties;

  @Mock private WebErrorProperties webProperties;

  @Mock private TraceProvider traceProvider;

  @Mock private ProblemFieldContributor coreFieldContributor;

  @Mock private WebProblemFieldContributor webFieldContributor;

  private ProblemDetailBuilder builder;

  @BeforeEach
  void setUp() {
    when(webProperties.getTypeBaseUrl()).thenReturn("https://api.patra.com/errors");

    builder =
        new ProblemDetailBuilder(
            errorProperties,
            webProperties,
            traceProvider,
            List.of(coreFieldContributor),
            List.of(webFieldContributor));
  }

  @Test
  @DisplayName("应该构建包含所有标准字段的 ProblemDetail")
  void shouldBuildProblemDetailWithAllStandardFields() {
    // Given: 准备错误解析和请求
    ErrorCodeLike errorCode = mock(ErrorCodeLike.class);
    when(errorCode.code()).thenReturn("ERR_TEST_ERROR");

    ErrorResolution resolution = mock(ErrorResolution.class);
    when(resolution.errorCode()).thenReturn(errorCode);
    when(resolution.httpStatus()).thenReturn(400);

    Throwable exception = new RuntimeException("测试错误消息");

    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getRequestURI()).thenReturn("/api/test");

    when(traceProvider.getCurrentTraceId()).thenReturn(Optional.empty());

    // When: 构建 ProblemDetail
    ProblemDetail result = builder.build(resolution, exception, request);

    // Then: 验证标准字段
    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(400);
    assertThat(result.getTitle()).isEqualTo("ERR_TEST_ERROR");
    assertThat(result.getDetail()).isEqualTo("测试错误消息");
    assertThat(result.getType().toString())
        .isEqualTo("https://api.patra.com/errors/err_test_error");
    assertThat(result.getProperties()).containsKey(ErrorKeys.CODE);
    assertThat(result.getProperties().get(ErrorKeys.CODE)).isEqualTo("ERR_TEST_ERROR");
    assertThat(result.getProperties()).containsKey(ErrorKeys.PATH);
    assertThat(result.getProperties().get(ErrorKeys.PATH)).isEqualTo("/api/test");
    assertThat(result.getProperties()).containsKey(ErrorKeys.TIMESTAMP);
  }

  @Test
  @DisplayName("应该在可用时添加 trace ID")
  void shouldAddTraceIdWhenAvailable() {
    // Given: 准备带有 trace ID 的场景
    ErrorCodeLike errorCode = mock(ErrorCodeLike.class);
    when(errorCode.code()).thenReturn("ERR_TEST");

    ErrorResolution resolution = mock(ErrorResolution.class);
    when(resolution.errorCode()).thenReturn(errorCode);
    when(resolution.httpStatus()).thenReturn(500);

    Throwable exception = new RuntimeException("测试");
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getRequestURI()).thenReturn("/api/test");

    when(traceProvider.getCurrentTraceId()).thenReturn(Optional.of("trace-123"));

    // When: 构建 ProblemDetail
    ProblemDetail result = builder.build(resolution, exception, request);

    // Then: 验证 trace ID
    assertThat(result.getProperties()).containsKey(ErrorKeys.TRACE_ID);
    assertThat(result.getProperties().get(ErrorKeys.TRACE_ID)).isEqualTo("trace-123");
  }

  @Test
  @DisplayName("应该掩码敏感数据")
  void shouldMaskSensitiveData() {
    // Given: 准备包含敏感信息的异常
    ErrorCodeLike errorCode = mock(ErrorCodeLike.class);
    when(errorCode.code()).thenReturn("ERR_AUTH");

    ErrorResolution resolution = mock(ErrorResolution.class);
    when(resolution.errorCode()).thenReturn(errorCode);
    when(resolution.httpStatus()).thenReturn(401);

    Throwable exception = new RuntimeException("认证失败: password=secret123, token=abc123");
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getRequestURI()).thenReturn("/api/auth");

    when(traceProvider.getCurrentTraceId()).thenReturn(Optional.empty());

    // When: 构建 ProblemDetail
    ProblemDetail result = builder.build(resolution, exception, request);

    // Then: 验证敏感信息被掩码
    assertThat(result.getDetail()).contains("password=***");
    assertThat(result.getDetail()).contains("token=***");
    assertThat(result.getDetail()).doesNotContain("secret123");
    assertThat(result.getDetail()).doesNotContain("abc123");
  }

  @Test
  @DisplayName("应该调用核心字段贡献器")
  void shouldInvokeCoreFieldContributors() {
    // Given: 准备贡献器
    ErrorCodeLike errorCode = mock(ErrorCodeLike.class);
    when(errorCode.code()).thenReturn("ERR_TEST");

    ErrorResolution resolution = mock(ErrorResolution.class);
    when(resolution.errorCode()).thenReturn(errorCode);
    when(resolution.httpStatus()).thenReturn(500);

    Throwable exception = new RuntimeException("测试");
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getRequestURI()).thenReturn("/api/test");

    when(traceProvider.getCurrentTraceId()).thenReturn(Optional.empty());

    // When: 构建 ProblemDetail
    builder.build(resolution, exception, request);

    // Then: 验证贡献器被调用
    verify(coreFieldContributor).contribute(any(Map.class), eq(exception));
  }

  @Test
  @DisplayName("应该调用 Web 字段贡献器")
  void shouldInvokeWebFieldContributors() {
    // Given: 准备贡献器
    ErrorCodeLike errorCode = mock(ErrorCodeLike.class);
    when(errorCode.code()).thenReturn("ERR_TEST");

    ErrorResolution resolution = mock(ErrorResolution.class);
    when(resolution.errorCode()).thenReturn(errorCode);
    when(resolution.httpStatus()).thenReturn(500);

    Throwable exception = new RuntimeException("测试");
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getRequestURI()).thenReturn("/api/test");

    when(traceProvider.getCurrentTraceId()).thenReturn(Optional.empty());

    // When: 构建 ProblemDetail
    builder.build(resolution, exception, request);

    // Then: 验证贡献器被调用
    verify(webFieldContributor).contribute(any(Map.class), eq(exception), eq(request));
  }

  @Test
  @DisplayName("应该在贡献器失败时继续构建")
  void shouldContinueBuildingWhenContributorFails() {
    // Given: 准备失败的贡献器
    ErrorCodeLike errorCode = mock(ErrorCodeLike.class);
    when(errorCode.code()).thenReturn("ERR_TEST");

    ErrorResolution resolution = mock(ErrorResolution.class);
    when(resolution.errorCode()).thenReturn(errorCode);
    when(resolution.httpStatus()).thenReturn(500);

    Throwable exception = new RuntimeException("测试");
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getRequestURI()).thenReturn("/api/test");

    when(traceProvider.getCurrentTraceId()).thenReturn(Optional.empty());

    doThrow(new RuntimeException("贡献器失败"))
        .when(coreFieldContributor)
        .contribute(any(Map.class), eq(exception));

    // When: 构建 ProblemDetail
    ProblemDetail result = builder.build(resolution, exception, request);

    // Then: 验证仍然构建成功
    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(500);
  }

  @Test
  @DisplayName("应该处理代理感知的路径提取 - Forwarded 头")
  void shouldExtractProxyAwarePathFromForwardedHeader() {
    // Given: 准备带有 Forwarded 头的请求
    ErrorCodeLike errorCode = mock(ErrorCodeLike.class);
    when(errorCode.code()).thenReturn("ERR_TEST");

    ErrorResolution resolution = mock(ErrorResolution.class);
    when(resolution.errorCode()).thenReturn(errorCode);
    when(resolution.httpStatus()).thenReturn(500);

    Throwable exception = new RuntimeException("测试");
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getHeader("Forwarded")).thenReturn("path=\"/original/path\";host=example.com");

    when(traceProvider.getCurrentTraceId()).thenReturn(Optional.empty());

    // When: 构建 ProblemDetail
    ProblemDetail result = builder.build(resolution, exception, request);

    // Then: 验证使用 Forwarded 头中的路径
    assertThat(result.getProperties().get(ErrorKeys.PATH)).isEqualTo("/original/path");
  }

  @Test
  @DisplayName("应该处理代理感知的路径提取 - X-Forwarded-Path 头")
  void shouldExtractProxyAwarePathFromXForwardedPath() {
    // Given: 准备带有 X-Forwarded-Path 头的请求
    ErrorCodeLike errorCode = mock(ErrorCodeLike.class);
    when(errorCode.code()).thenReturn("ERR_TEST");

    ErrorResolution resolution = mock(ErrorResolution.class);
    when(resolution.errorCode()).thenReturn(errorCode);
    when(resolution.httpStatus()).thenReturn(500);

    Throwable exception = new RuntimeException("测试");
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getHeader("Forwarded")).thenReturn(null);
    when(request.getHeader("X-Forwarded-Path")).thenReturn("/x-forwarded-path");

    when(traceProvider.getCurrentTraceId()).thenReturn(Optional.empty());

    // When: 构建 ProblemDetail
    ProblemDetail result = builder.build(resolution, exception, request);

    // Then: 验证使用 X-Forwarded-Path
    assertThat(result.getProperties().get(ErrorKeys.PATH)).isEqualTo("/x-forwarded-path");
  }

  @Test
  @DisplayName("应该将 HTTP 状态转换为 HttpStatus 枚举")
  void shouldConvertHttpStatusCodeToEnum() {
    // Given: 准备不同的状态码
    ErrorCodeLike errorCode = mock(ErrorCodeLike.class);
    when(errorCode.code()).thenReturn("ERR_NOT_FOUND");

    ErrorResolution resolution = mock(ErrorResolution.class);
    when(resolution.errorCode()).thenReturn(errorCode);
    when(resolution.httpStatus()).thenReturn(404);

    Throwable exception = new RuntimeException("未找到");
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getRequestURI()).thenReturn("/api/resource");

    when(traceProvider.getCurrentTraceId()).thenReturn(Optional.empty());

    // When: 构建 ProblemDetail
    ProblemDetail result = builder.build(resolution, exception, request);

    // Then: 验证状态码
    assertThat(result.getStatus()).isEqualTo(404);
  }
}
