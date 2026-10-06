package dev.linqibin.starter.web.error.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.linqibin.commons.error.codes.ErrorCodeLike;
import dev.linqibin.commons.error.field.FieldViolation;
import dev.linqibin.commons.error.problem.ErrorKeys;
import dev.linqibin.starter.core.error.model.ErrorResolution;
import dev.linqibin.starter.core.error.model.ResolutionStrategy;
import dev.linqibin.starter.web.error.FieldViolationsException;
import dev.linqibin.starter.web.error.RetryAfterException;
import dev.linqibin.starter.web.error.adapter.ProblemDetailAdapter;
import dev.linqibin.starter.web.error.adapter.model.ProblemDetailResponse;
import dev.linqibin.starter.web.error.model.ValidationError;
import dev.linqibin.starter.web.error.spi.ValidationErrorsFormatter;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
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
  private Logger handlerLogger;
  private ListAppender<ILoggingEvent> logAppender;

  @BeforeEach
  void setUp() {
    handler = new GlobalRestExceptionHandler(problemDetailAdapter, validationErrorsFormatter);
    handlerLogger = (Logger) LoggerFactory.getLogger(GlobalRestExceptionHandler.class);
    logAppender = new ListAppender<>();
    logAppender.start();
    handlerLogger.addAppender(logAppender);
  }

  /// 摘掉日志收集器，避免影响别的测试。
  @AfterEach
  void detachLogAppender() {
    handlerLogger.detachAppender(logAppender);
  }

  @Test
  @DisplayName("优先级应比最高优先级低一级，给安全异常处理器留出位置")
  void shouldRankOneBelowHighestPrecedence() {
    Order order = AnnotationUtils.findAnnotation(GlobalRestExceptionHandler.class, Order.class);

    assertThat(order).isNotNull();
    assertThat(order.value()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 1);
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
        List.of(new ValidationError("email", "EMAIL", "invalid", "必须是有效的邮箱"));

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
            .mapToObj(i -> new ValidationError("field" + i, "SIZE", "value" + i, "message" + i))
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
        List.of(new ValidationError("field", "SIZE", "value", "message"));

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

  @Test
  @DisplayName("异常带剩余等待时间时加上 Retry-After 响应头")
  void should_add_retry_after_header_when_exception_has_retry_after() {
    Exception exception = new RetryAfterException(Duration.ofMinutes(15));
    HttpServletRequest request = mock(HttpServletRequest.class);
    ProblemDetailResponse tooMany = response(HttpStatus.TOO_MANY_REQUESTS, "TEST-0429");
    when(problemDetailAdapter.adapt(exception, request)).thenReturn(tooMany);

    ResponseEntity<ProblemDetail> result = handler.handleException(exception, request);

    assertThat(result.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("900");
  }

  @Test
  @DisplayName("普通异常不加 Retry-After 响应头")
  void should_not_add_retry_after_header_for_plain_exception() {
    Exception exception = new IllegalStateException("x");
    HttpServletRequest request = mock(HttpServletRequest.class);
    ProblemDetailResponse conflict = response(HttpStatus.CONFLICT, "TEST-0409");
    when(problemDetailAdapter.adapt(exception, request)).thenReturn(conflict);

    ResponseEntity<ProblemDetail> result = handler.handleException(exception, request);

    assertThat(result.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNull();
  }

  /// 构造一个指定状态和错误码的适配结果。
  ///
  /// @param status HTTP 状态
  /// @param code 错误码
  /// @return 适配结果
  private static ProblemDetailResponse response(HttpStatus status, String code) {
    ErrorCodeLike errorCode = mock(ErrorCodeLike.class);
    when(errorCode.code()).thenReturn(code);
    ErrorResolution errorResolution = mock(ErrorResolution.class);
    when(errorResolution.errorCode()).thenReturn(errorCode);
    return new ProblemDetailResponse(ProblemDetail.forStatus(status), status, errorResolution);
  }

  @Test
  @DisplayName("4xx 记 WARN，不带堆栈")
  void should_log_client_error_at_warn_without_stack_trace() {
    Exception exception = new IllegalStateException("邮箱或密码错误");
    HttpServletRequest request = mock(HttpServletRequest.class);
    ProblemDetailResponse unauthorized = response(HttpStatus.UNAUTHORIZED, "TEST-0401");
    when(problemDetailAdapter.adapt(exception, request)).thenReturn(unauthorized);

    handler.handleException(exception, request);

    assertThat(logAppender.list)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.WARN);
              assertThat(event.getThrowableProxy()).isNull();
              assertThat(event.getFormattedMessage()).contains("TEST-0401").contains("邮箱或密码错误");
            });
  }

  @Test
  @DisplayName("5xx 记 ERROR，带堆栈")
  void should_log_server_error_at_error_with_stack_trace() {
    Exception exception = new IllegalStateException("连接池耗尽");
    HttpServletRequest request = mock(HttpServletRequest.class);
    ProblemDetailResponse serverError = response(HttpStatus.INTERNAL_SERVER_ERROR, "TEST-0500");
    when(problemDetailAdapter.adapt(exception, request)).thenReturn(serverError);

    handler.handleException(exception, request);

    assertThat(logAppender.list)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.ERROR);
              assertThat(event.getThrowableProxy()).isNotNull();
            });
  }

  @Test
  @DisplayName("参数校验失败：detail 用固定文案，日志只记字段名和原因码")
  void should_hide_rejected_values_when_validation_fails() throws Exception {
    BindingResult bindingResult = mock(BindingResult.class);
    Method method = getClass().getDeclaredMethod("dummyMethod", String.class);
    MethodArgumentNotValidException exception =
        new MethodArgumentNotValidException(new MethodParameter(method, 0), bindingResult);
    ServletWebRequest webRequest = new ServletWebRequest(mock(HttpServletRequest.class));
    ProblemDetailResponse response = response(HttpStatus.valueOf(422), "TEST-0422");
    response.problemDetail().setDetail("rejected value [Leaky-Secret-123]");
    when(problemDetailAdapter.adapt(eq(exception), any(HttpServletRequest.class)))
        .thenReturn(response);
    when(validationErrorsFormatter.formatWithMasking(bindingResult))
        .thenReturn(List.of(new ValidationError("password", "SIZE", "***", "长度不对")));

    ResponseEntity<Object> result =
        handler.handleMethodArgumentNotValid(exception, null, HttpStatus.valueOf(422), webRequest);

    ProblemDetail body = (ProblemDetail) result.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getDetail()).isEqualTo("请求参数不合法");
    assertThat(logAppender.list)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.WARN);
              assertThat(event.getThrowableProxy()).isNull();
              assertThat(event.getFormattedMessage())
                  .contains("参数校验失败")
                  .contains("password:SIZE")
                  .doesNotContain("Leaky-Secret-123");
            });
  }

  @Test
  @DisplayName("剩余等待时间为 null 时不加 Retry-After 响应头，也不出错")
  void should_skip_retry_after_header_when_value_is_null() {
    Exception exception = new RetryAfterException(null);
    HttpServletRequest request = mock(HttpServletRequest.class);
    ProblemDetailResponse tooMany = response(HttpStatus.TOO_MANY_REQUESTS, "TEST-0429");
    when(problemDetailAdapter.adapt(exception, request)).thenReturn(tooMany);

    ResponseEntity<ProblemDetail> result = handler.handleException(exception, request);

    assertThat(result.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNull();
  }

  @Test
  @DisplayName("领域层报的字段错误：WARN 日志带上「字段:原因码」，不带字段值和堆栈")
  void should_log_field_codes_for_domain_field_violations() {
    Exception exception =
        new FieldViolationsException(
            List.of(FieldViolation.of("password", "TOO_COMMON", "这个密码太常见")));
    HttpServletRequest request = mock(HttpServletRequest.class);
    ProblemDetailResponse invalid = response(HttpStatus.UNPROCESSABLE_CONTENT, "TEST-0422");
    when(problemDetailAdapter.adapt(exception, request)).thenReturn(invalid);

    handler.handleException(exception, request);

    assertThat(logAppender.list)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.WARN);
              assertThat(event.getThrowableProxy()).isNull();
              assertThat(event.getFormattedMessage()).contains("password:TOO_COMMON");
            });
  }

  @ParameterizedTest
  @EnumSource(
      value = ResolutionStrategy.class,
      names = {"FALLBACK", "CAUSE"})
  @DisplayName("按类名兜底分类出来的 4xx 可能是服务端缺陷：记 WARN，但保留堆栈")
  void should_keep_stack_trace_for_inferred_client_error(ResolutionStrategy strategy) {
    Exception exception = new IllegalStateException("不变量被破坏");
    HttpServletRequest request = mock(HttpServletRequest.class);
    ProblemDetailResponse inferred =
        response(HttpStatus.UNPROCESSABLE_CONTENT, "TEST-0422", strategy);
    when(problemDetailAdapter.adapt(exception, request)).thenReturn(inferred);

    handler.handleException(exception, request);

    assertThat(logAppender.list)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.WARN);
              assertThat(event.getThrowableProxy()).isNotNull();
            });
  }

  /// 构造一个指定状态、错误码和解析策略的适配结果。
  ///
  /// @param status HTTP 状态
  /// @param code 错误码
  /// @param strategy 解析策略
  /// @return 适配结果
  private static ProblemDetailResponse response(
      HttpStatus status, String code, ResolutionStrategy strategy) {
    ProblemDetailResponse response = response(status, code);
    when(response.errorResolution().strategy()).thenReturn(strategy);
    return response;
  }
}
