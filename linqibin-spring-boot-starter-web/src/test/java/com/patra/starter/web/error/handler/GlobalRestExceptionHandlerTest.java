package com.patra.starter.web.error.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.starter.core.error.model.ErrorResolution;
import com.patra.starter.web.error.adapter.ProblemDetailAdapter;
import com.patra.starter.web.error.adapter.model.ProblemDetailResponse;
import com.patra.starter.web.error.model.ValidationError;
import com.patra.starter.web.error.spi.ValidationErrorsFormatter;
import dev.linqibin.commons.error.problem.ErrorKeys;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;

/// GlobalRestExceptionHandler 单元测试。
@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalRestExceptionHandler 单元测试")
class GlobalRestExceptionHandlerTest {

  @Mock private ProblemDetailAdapter problemDetailAdapter;

  @Mock private ValidationErrorsFormatter validationErrorsFormatter;

  private GlobalRestExceptionHandler handler;

  @BeforeEach
  void setUp() {
    handler = new GlobalRestExceptionHandler(problemDetailAdapter, validationErrorsFormatter);
  }

  @Test
  @DisplayName("应该处理通用异常并返回 ProblemDetail 响应")
  void shouldHandleGenericException() {
    // Given: 准备异常和响应
    Exception exception = new RuntimeException("测试异常");
    HttpServletRequest request = mock(HttpServletRequest.class);

    ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);

    dev.linqibin.commons.error.codes.ErrorCodeLike errorCode =
        mock(dev.linqibin.commons.error.codes.ErrorCodeLike.class);
    when(errorCode.code()).thenReturn("ERR_INTERNAL_ERROR");

    ErrorResolution errorResolution = mock(ErrorResolution.class);
    when(errorResolution.errorCode()).thenReturn(errorCode);

    ProblemDetailResponse response =
        new ProblemDetailResponse(problemDetail, HttpStatus.INTERNAL_SERVER_ERROR, errorResolution);

    when(problemDetailAdapter.adapt(exception, request)).thenReturn(response);

    // When: 处理异常
    ResponseEntity<ProblemDetail> result = handler.handleException(exception, request);

    // Then: 验证响应
    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(result.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(result.getBody()).isEqualTo(problemDetail);

    verify(problemDetailAdapter).adapt(exception, request);
  }

  @Test
  @DisplayName("应该处理验证异常并附加验证错误")
  void shouldHandleMethodArgumentNotValidException() throws Exception {
    // Given: 准备验证异常
    BindingResult bindingResult = mock(BindingResult.class);

    // 创建一个真实的 MethodParameter (避免 Mock 导致的 NullPointerException)
    java.lang.reflect.Method method = getClass().getDeclaredMethod("dummyMethod", String.class);
    org.springframework.core.MethodParameter methodParameter =
        new org.springframework.core.MethodParameter(method, 0);

    MethodArgumentNotValidException exception =
        new MethodArgumentNotValidException(methodParameter, bindingResult);

    HttpServletRequest servletRequest = mock(HttpServletRequest.class);
    ServletWebRequest webRequest = new ServletWebRequest(servletRequest);

    ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);

    dev.linqibin.commons.error.codes.ErrorCodeLike errorCode =
        mock(dev.linqibin.commons.error.codes.ErrorCodeLike.class);
    when(errorCode.code()).thenReturn("ERR_VALIDATION_FAILED");

    ErrorResolution errorResolution = mock(ErrorResolution.class);
    when(errorResolution.errorCode()).thenReturn(errorCode);

    ProblemDetailResponse response =
        new ProblemDetailResponse(problemDetail, HttpStatus.BAD_REQUEST, errorResolution);

    List<ValidationError> validationErrors =
        List.of(new ValidationError("email", "invalid", "必须是有效的邮箱"));

    when(problemDetailAdapter.adapt(eq(exception), any(HttpServletRequest.class)))
        .thenReturn(response);
    when(validationErrorsFormatter.formatWithMasking(bindingResult)).thenReturn(validationErrors);

    // When: 处理验证异常
    ResponseEntity<Object> result =
        handler.handleMethodArgumentNotValid(exception, null, HttpStatus.BAD_REQUEST, webRequest);

    // Then: 验证响应
    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(result.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

    ProblemDetail resultBody = (ProblemDetail) result.getBody();
    assertThat(resultBody).isNotNull();
    assertThat(resultBody.getProperties()).containsKey(ErrorKeys.ERRORS);
    assertThat(resultBody.getProperties().get(ErrorKeys.ERRORS)).isEqualTo(validationErrors);

    verify(validationErrorsFormatter).formatWithMasking(bindingResult);
  }

  @Test
  @DisplayName("应该截断超过最大数量的验证错误")
  void shouldTruncateValidationErrorsWhenExceedingMaximum() throws Exception {
    // Given: 准备包含大量错误的验证异常
    BindingResult bindingResult = mock(BindingResult.class);

    // 创建一个真实的 MethodParameter
    java.lang.reflect.Method method = getClass().getDeclaredMethod("dummyMethod", String.class);
    org.springframework.core.MethodParameter methodParameter =
        new org.springframework.core.MethodParameter(method, 0);

    MethodArgumentNotValidException exception =
        new MethodArgumentNotValidException(methodParameter, bindingResult);

    HttpServletRequest servletRequest = mock(HttpServletRequest.class);
    ServletWebRequest webRequest = new ServletWebRequest(servletRequest);

    ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);

    dev.linqibin.commons.error.codes.ErrorCodeLike errorCode =
        mock(dev.linqibin.commons.error.codes.ErrorCodeLike.class);
    when(errorCode.code()).thenReturn("ERR_VALIDATION_FAILED");

    ErrorResolution errorResolution = mock(ErrorResolution.class);
    when(errorResolution.errorCode()).thenReturn(errorCode);

    ProblemDetailResponse response =
        new ProblemDetailResponse(problemDetail, HttpStatus.BAD_REQUEST, errorResolution);

    // 创建 101 个验证错误（超过 MAX_VALIDATION_ERRORS = 100）
    List<ValidationError> allErrors =
        java.util.stream.IntStream.range(0, 101)
            .mapToObj(i -> new ValidationError("field" + i, "value" + i, "message" + i))
            .toList();

    when(problemDetailAdapter.adapt(eq(exception), any(HttpServletRequest.class)))
        .thenReturn(response);
    when(validationErrorsFormatter.formatWithMasking(bindingResult)).thenReturn(allErrors);

    // When: 处理验证异常
    ResponseEntity<Object> result =
        handler.handleMethodArgumentNotValid(exception, null, HttpStatus.BAD_REQUEST, webRequest);

    // Then: 验证仅返回前 100 个错误
    ProblemDetail resultBody = (ProblemDetail) result.getBody();
    assertThat(resultBody).isNotNull();
    @SuppressWarnings("unchecked")
    List<ValidationError> returnedErrors =
        (List<ValidationError>) resultBody.getProperties().get(ErrorKeys.ERRORS);
    assertThat(returnedErrors).hasSize(100);
  }

  @Test
  @DisplayName("应该处理非 ServletWebRequest 的 WebRequest")
  void shouldHandleNonServletWebRequest() throws Exception {
    // Given: 准备非 ServletWebRequest
    BindingResult bindingResult = mock(BindingResult.class);

    // 创建一个真实的 MethodParameter
    java.lang.reflect.Method method = getClass().getDeclaredMethod("dummyMethod", String.class);
    org.springframework.core.MethodParameter methodParameter =
        new org.springframework.core.MethodParameter(method, 0);

    MethodArgumentNotValidException exception =
        new MethodArgumentNotValidException(methodParameter, bindingResult);

    org.springframework.web.context.request.WebRequest webRequest =
        mock(org.springframework.web.context.request.WebRequest.class);

    ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);

    dev.linqibin.commons.error.codes.ErrorCodeLike errorCode =
        mock(dev.linqibin.commons.error.codes.ErrorCodeLike.class);
    when(errorCode.code()).thenReturn("ERR_VALIDATION_FAILED");

    ErrorResolution errorResolution = mock(ErrorResolution.class);
    when(errorResolution.errorCode()).thenReturn(errorCode);

    ProblemDetailResponse response =
        new ProblemDetailResponse(problemDetail, HttpStatus.BAD_REQUEST, errorResolution);

    List<ValidationError> validationErrors =
        List.of(new ValidationError("field", "value", "message"));

    when(problemDetailAdapter.adapt(eq(exception), any())).thenReturn(response);
    when(validationErrorsFormatter.formatWithMasking(bindingResult)).thenReturn(validationErrors);

    // When: 处理验证异常
    ResponseEntity<Object> result =
        handler.handleMethodArgumentNotValid(exception, null, HttpStatus.BAD_REQUEST, webRequest);

    // Then: 验证响应
    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("应该在 ProblemDetail 没有 properties 时安全处理")
  void shouldHandleProblemDetailWithoutProperties() {
    // Given: 准备没有 properties 的 ProblemDetail
    Exception exception = new RuntimeException("测试异常");
    HttpServletRequest request = mock(HttpServletRequest.class);

    ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
    problemDetail.setProperties(null); // 显式设置为 null

    dev.linqibin.commons.error.codes.ErrorCodeLike errorCode =
        mock(dev.linqibin.commons.error.codes.ErrorCodeLike.class);
    when(errorCode.code()).thenReturn("ERR_INTERNAL_ERROR");

    ErrorResolution errorResolution = mock(ErrorResolution.class);
    when(errorResolution.errorCode()).thenReturn(errorCode);

    ProblemDetailResponse response =
        new ProblemDetailResponse(problemDetail, HttpStatus.INTERNAL_SERVER_ERROR, errorResolution);

    when(problemDetailAdapter.adapt(exception, request)).thenReturn(response);

    // When: 处理异常
    ResponseEntity<ProblemDetail> result = handler.handleException(exception, request);

    // Then: 验证不抛出异常
    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @Test
  @DisplayName("应该使用正确的 Content-Type 响应")
  void shouldRespondWithCorrectContentType() {
    // Given: 准备异常
    Exception exception = new RuntimeException("测试异常");
    HttpServletRequest request = mock(HttpServletRequest.class);

    ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);

    dev.linqibin.commons.error.codes.ErrorCodeLike errorCode =
        mock(dev.linqibin.commons.error.codes.ErrorCodeLike.class);
    when(errorCode.code()).thenReturn("ERR_BAD_REQUEST");

    ErrorResolution errorResolution = mock(ErrorResolution.class);
    when(errorResolution.errorCode()).thenReturn(errorCode);

    ProblemDetailResponse response =
        new ProblemDetailResponse(problemDetail, HttpStatus.BAD_REQUEST, errorResolution);

    when(problemDetailAdapter.adapt(exception, request)).thenReturn(response);

    // When: 处理异常
    ResponseEntity<ProblemDetail> result = handler.handleException(exception, request);

    // Then: 验证 Content-Type
    assertThat(result.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
  }

  @Test
  @DisplayName("应该返回与错误解析相匹配的 HTTP 状态码")
  void shouldReturnHttpStatusMatchingErrorResolution() {
    // Given: 准备不同的 HTTP 状态码
    Exception exception = new RuntimeException("测试异常");
    HttpServletRequest request = mock(HttpServletRequest.class);

    ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);

    dev.linqibin.commons.error.codes.ErrorCodeLike errorCode =
        mock(dev.linqibin.commons.error.codes.ErrorCodeLike.class);
    when(errorCode.code()).thenReturn("ERR_NOT_FOUND");

    ErrorResolution errorResolution = mock(ErrorResolution.class);
    when(errorResolution.errorCode()).thenReturn(errorCode);

    ProblemDetailResponse response =
        new ProblemDetailResponse(problemDetail, HttpStatus.NOT_FOUND, errorResolution);

    when(problemDetailAdapter.adapt(exception, request)).thenReturn(response);

    // When: 处理异常
    ResponseEntity<ProblemDetail> result = handler.handleException(exception, request);

    // Then: 验证状态码
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  /// 用于测试的虚拟方法。
  @SuppressWarnings("unused")
  private void dummyMethod(String param) {
    // 仅用于创建 MethodParameter
  }
}
