package com.patra.starter.web.error.handler;

import com.patra.common.error.problem.ErrorKeys;
import com.patra.starter.web.error.adapter.ProblemDetailAdapter;
import com.patra.starter.web.error.adapter.model.ProblemDetailResponse;
import com.patra.starter.web.error.model.ValidationError;
import com.patra.starter.web.error.spi.ValidationErrorsFormatter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 全局 REST 异常处理器,使用共享平台错误解析管道呈现 RFC 7807 {@link ProblemDetail} 文档。
 *
 * <p>此处理器负责:
 *
 * <ul>
 *   <li>捕获所有未处理的异常({@code @ExceptionHandler(Exception.class)})
 *   <li>使用 {@link ProblemDetailAdapter} 将异常转换为 {@link ProblemDetail}
 *   <li>处理验证异常({@link MethodArgumentNotValidException}),附加验证错误列表
 *   <li>掩码敏感字段(通过 {@link ValidationErrorsFormatter})
 *   <li>返回符合 RFC 7807 标准的 JSON 响应(Content-Type: application/problem+json)
 * </ul>
 *
 * <p><b>响应格式示例:</b>
 *
 * <pre>{@code
 * {
 *   "type": "about:blank",
 *   "title": "Bad Request",
 *   "status": 400,
 *   "detail": "Validation failed for object='userRequest'",
 *   "instance": "/api/users",
 *   "errorCode": "ERR_VALIDATION_FAILED",
 *   "path": "/api/users",
 *   "errors": [
 *     { "field": "email", "code": "Email", "message": "must be a valid email" }
 *   ]
 * }
 * }</pre>
 *
 * <p><b>优先级:</b> {@link Ordered#HIGHEST_PRECEDENCE},确保在其他异常处理器之前执行。
 *
 * @see ProblemDetailAdapter
 * @see ValidationErrorsFormatter
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalRestExceptionHandler extends ResponseEntityExceptionHandler {

  /** 附加到问题详情载荷的验证错误的最大数量。 */
  private static final int MAX_VALIDATION_ERRORS = 100;

  private final ProblemDetailAdapter problemDetailAdapter;
  private final ValidationErrorsFormatter validationErrorsFormatter;

  public GlobalRestExceptionHandler(
      ProblemDetailAdapter problemDetailAdapter,
      ValidationErrorsFormatter validationErrorsFormatter) {
    this.problemDetailAdapter = problemDetailAdapter;
    this.validationErrorsFormatter = validationErrorsFormatter;
  }

  /** 后备处理器，将任何未捕获的异常转换为问题详情文档。 */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleException(Exception ex, HttpServletRequest request) {
    ProblemDetailResponse response = problemDetailAdapter.adapt(ex, request);

    logExceptionHandled(response, ex);

    return ResponseEntity.status(response.httpStatus())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(response.problemDetail());
  }

  /** 处理验证失败并将清理后的字段错误附加到响应载荷。 */
  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      @NonNull MethodArgumentNotValidException ex,
      @NonNull org.springframework.http.HttpHeaders headers,
      @NonNull org.springframework.http.HttpStatusCode status,
      @NonNull org.springframework.web.context.request.WebRequest request) {

    HttpServletRequest servletRequest = extractServletRequest(request);
    ProblemDetailResponse response = problemDetailAdapter.adapt(ex, servletRequest);

    List<ValidationError> errors = formatAndTruncateValidationErrors(ex);
    response.problemDetail().setProperty(ErrorKeys.ERRORS, errors);

    logValidationExceptionHandled(response, errors, ex);

    return ResponseEntity.status(response.httpStatus())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(response.problemDetail());
  }

  /**
   * 从 Spring 的 WebRequest 包装器中提取 HttpServletRequest。
   *
   * @param request web 请求包装器
   * @return servlet 请求或 null（如果不可用）
   */
  private HttpServletRequest extractServletRequest(
      org.springframework.web.context.request.WebRequest request) {
    if (request
        instanceof org.springframework.web.context.request.ServletWebRequest servletWebRequest) {
      return servletWebRequest.getRequest();
    }
    return null;
  }

  /**
   * 格式化验证错误并进行掩码，截断到允许的最大计数。
   *
   * @param ex 包含绑定结果的验证异常
   * @return 格式化和截断的验证错误列表
   */
  private List<ValidationError> formatAndTruncateValidationErrors(
      MethodArgumentNotValidException ex) {
    List<ValidationError> errors =
        validationErrorsFormatter.formatWithMasking(ex.getBindingResult());

    if (errors.size() > MAX_VALIDATION_ERRORS) {
      log.warn("验证错误超出最大限制：total={}，截断为 {}", errors.size(), MAX_VALIDATION_ERRORS);
      return errors.subList(0, MAX_VALIDATION_ERRORS);
    }

    return errors;
  }

  /**
   * 记录通用异常处理，包括错误代码、状态、请求路径和完整堆栈跟踪。
   *
   * @param response 包含错误元数据的问题详情响应
   * @param ex 被处理的异常
   */
  private void logExceptionHandled(ProblemDetailResponse response, Exception ex) {
    Object path = extractPathFromProblemDetail(response.problemDetail());
    log.error(
        "Exception handled: error code [{}], HTTP status {}, request path [{}], exception={}",
        response.errorResolution().errorCode().code(),
        response.httpStatus().value(),
        path,
        ex.getClass().getSimpleName(),
        ex);
  }

  /**
   * 记录验证异常处理，包括验证错误计数、元数据和堆栈跟踪。
   *
   * @param response 问题详情响应
   * @param errors 响应中包含的验证错误
   * @param ex 被处理的验证异常
   */
  private void logValidationExceptionHandled(
      ProblemDetailResponse response, List<ValidationError> errors, Exception ex) {
    Object path = extractPathFromProblemDetail(response.problemDetail());
    log.error(
        "Validation exception handled: error code [{}], {} validation errors, "
            + "HTTP status {}, request path [{}]",
        response.errorResolution().errorCode().code(),
        errors.size(),
        response.httpStatus().value(),
        path,
        ex);
  }

  /**
   * 安全地从问题详情中提取路径属性。
   *
   * @param problemDetail 问题详情实例
   * @return 路径值，不存在时返回 null
   */
  private Object extractPathFromProblemDetail(ProblemDetail problemDetail) {
    return problemDetail.getProperties() == null
        ? null
        : problemDetail.getProperties().get(ErrorKeys.PATH);
  }
}
