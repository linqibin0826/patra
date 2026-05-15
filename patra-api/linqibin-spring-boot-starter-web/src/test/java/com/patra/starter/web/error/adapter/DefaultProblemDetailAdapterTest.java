package com.patra.starter.web.error.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.common.error.codes.ErrorCodeLike;
import com.patra.starter.core.error.model.ErrorResolution;
import com.patra.starter.core.error.model.ResolutionStrategy;
import com.patra.starter.core.error.pipeline.ErrorResolutionPipeline;
import com.patra.starter.web.error.adapter.model.ProblemDetailResponse;
import com.patra.starter.web.error.builder.ProblemDetailBuilder;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/// 测试 {@link DefaultProblemDetailAdapter} 的异常转换和 ProblemDetail 构建
@DisplayName("DefaultProblemDetailAdapter 单元测试")
@ExtendWith(MockitoExtension.class)
class DefaultProblemDetailAdapterTest {

  @Mock private ErrorResolutionPipeline pipeline;

  @Mock private ProblemDetailBuilder problemDetailBuilder;

  @Mock private HttpServletRequest request;

  private DefaultProblemDetailAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new DefaultProblemDetailAdapter(pipeline, problemDetailBuilder);
  }

  @Test
  @DisplayName("adapt() 应该调用 pipeline 解析异常")
  void adapt_shouldInvokePipelineToResolveException() {
    // Arrange
    Exception exception = new IllegalArgumentException("test exception");
    ErrorResolution resolution = createMockResolution(400, "INVALID_INPUT");
    ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);

    when(pipeline.resolve(exception)).thenReturn(resolution);
    when(problemDetailBuilder.build(eq(resolution), eq(exception), eq(request)))
        .thenReturn(problemDetail);

    // Act
    adapter.adapt(exception, request);

    // Assert
    verify(pipeline).resolve(exception);
  }

  @Test
  @DisplayName("adapt() 应该调用 builder 构建 ProblemDetail")
  void adapt_shouldInvokeBuilderToConstructProblemDetail() {
    // Arrange
    Exception exception = new RuntimeException("test");
    ErrorResolution resolution = createMockResolution(500, "INTERNAL_ERROR");
    ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);

    when(pipeline.resolve(exception)).thenReturn(resolution);
    when(problemDetailBuilder.build(eq(resolution), eq(exception), eq(request)))
        .thenReturn(problemDetail);

    // Act
    adapter.adapt(exception, request);

    // Assert
    verify(problemDetailBuilder).build(resolution, exception, request);
  }

  @Test
  @DisplayName("adapt() 应该返回包含 ProblemDetail、HttpStatus 和 ErrorResolution 的响应")
  void adapt_shouldReturnResponseWithAllComponents() {
    // Arrange
    Exception exception = new IllegalStateException("invalid state");
    ErrorResolution resolution = createMockResolution(409, "CONFLICT");
    ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.CONFLICT);

    when(pipeline.resolve(exception)).thenReturn(resolution);
    when(problemDetailBuilder.build(any(), any(), any())).thenReturn(problemDetail);

    // Act
    ProblemDetailResponse response = adapter.adapt(exception, request);

    // Assert
    assertThat(response).isNotNull();
    assertThat(response.problemDetail()).isEqualTo(problemDetail);
    assertThat(response.httpStatus()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.errorResolution()).isEqualTo(resolution);
  }

  @Test
  @DisplayName("adapt() 应该正确转换 HTTP 状态码为 HttpStatus")
  void adapt_shouldConvertHttpStatusCodeCorrectly() {
    // Arrange
    Exception exception = new Exception("not found");
    ErrorResolution resolution = createMockResolution(404, "NOT_FOUND");
    ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);

    when(pipeline.resolve(exception)).thenReturn(resolution);
    when(problemDetailBuilder.build(any(), any(), any())).thenReturn(problemDetail);

    // Act
    ProblemDetailResponse response = adapter.adapt(exception, request);

    // Assert
    assertThat(response.httpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("adapt() 处理 null 异常应该不抛出异常")
  void adapt_withNullException_shouldNotThrow() {
    // Arrange
    ErrorResolution resolution = createMockResolution(500, "INTERNAL_ERROR");
    ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);

    when(pipeline.resolve(null)).thenReturn(resolution);
    when(problemDetailBuilder.build(any(), eq(null), any())).thenReturn(problemDetail);

    // Act & Assert
    ProblemDetailResponse response = adapter.adapt(null, request);
    assertThat(response).isNotNull();
  }

  @Test
  @DisplayName("adapt() 应该处理各种 HTTP 状态码")
  void adapt_shouldHandleVariousHttpStatuses() {
    // 测试多种 HTTP 状态码
    testHttpStatusConversion(400, HttpStatus.BAD_REQUEST);
    testHttpStatusConversion(401, HttpStatus.UNAUTHORIZED);
    testHttpStatusConversion(403, HttpStatus.FORBIDDEN);
    testHttpStatusConversion(404, HttpStatus.NOT_FOUND);
    testHttpStatusConversion(500, HttpStatus.INTERNAL_SERVER_ERROR);
    testHttpStatusConversion(503, HttpStatus.SERVICE_UNAVAILABLE);
  }

  @Test
  @DisplayName("adapt() 应该传递 request 到 builder")
  void adapt_shouldPassRequestToBuilder() {
    // Arrange
    Exception exception = new RuntimeException("test");
    ErrorResolution resolution = createMockResolution(500, "ERROR");
    ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);

    when(pipeline.resolve(exception)).thenReturn(resolution);
    when(problemDetailBuilder.build(any(), any(), eq(request))).thenReturn(problemDetail);

    // Act
    adapter.adapt(exception, request);

    // Assert
    verify(problemDetailBuilder).build(any(), any(), eq(request));
  }

  @Test
  @DisplayName("adapt() 处理不同类型的异常应该正确解析")
  void adapt_withDifferentExceptionTypes_shouldResolveCorrectly() {
    // Test with IllegalArgumentException
    IllegalArgumentException illegalArgEx = new IllegalArgumentException("invalid arg");
    ErrorResolution resolution1 = createMockResolution(400, "BAD_REQUEST");
    ProblemDetail pd1 = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    when(pipeline.resolve(illegalArgEx)).thenReturn(resolution1);
    when(problemDetailBuilder.build(eq(resolution1), eq(illegalArgEx), any())).thenReturn(pd1);

    ProblemDetailResponse response1 = adapter.adapt(illegalArgEx, request);
    assertThat(response1.httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);

    // Test with NullPointerException
    NullPointerException npe = new NullPointerException("null value");
    ErrorResolution resolution2 = createMockResolution(500, "INTERNAL_ERROR");
    ProblemDetail pd2 = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
    when(pipeline.resolve(npe)).thenReturn(resolution2);
    when(problemDetailBuilder.build(eq(resolution2), eq(npe), any())).thenReturn(pd2);

    ProblemDetailResponse response2 = adapter.adapt(npe, request);
    assertThat(response2.httpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
  }

  // ==================== 辅助方法 ====================

  private void testHttpStatusConversion(int httpStatusCode, HttpStatus expectedHttpStatus) {
    Exception exception = new RuntimeException("test");
    ErrorResolution resolution = createMockResolution(httpStatusCode, "ERROR_CODE");
    ProblemDetail problemDetail = ProblemDetail.forStatus(expectedHttpStatus);

    when(pipeline.resolve(exception)).thenReturn(resolution);
    when(problemDetailBuilder.build(any(), any(), any())).thenReturn(problemDetail);

    ProblemDetailResponse response = adapter.adapt(exception, request);
    assertThat(response.httpStatus()).isEqualTo(expectedHttpStatus);
  }

  private ErrorResolution createMockResolution(int httpStatus, String errorCodeValue) {
    ErrorCodeLike errorCode = mock(ErrorCodeLike.class);
    when(errorCode.code()).thenReturn(errorCodeValue);
    // ErrorCodeLike.httpStatus() 在测试中不被调用，因为 ErrorResolution 直接存储 httpStatus

    return new ErrorResolution(errorCode, httpStatus, ResolutionStrategy.FALLBACK);
  }
}
