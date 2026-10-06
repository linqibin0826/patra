package dev.linqibin.starter.web.error.handler;

import dev.linqibin.commons.error.problem.ErrorKeys;
import dev.linqibin.commons.error.retry.HasRetryAfter;
import dev.linqibin.starter.core.error.model.ResolutionStrategy;
import dev.linqibin.starter.web.error.adapter.ProblemDetailAdapter;
import dev.linqibin.starter.web.error.adapter.model.ProblemDetailResponse;
import dev.linqibin.starter.web.error.model.ValidationError;
import dev.linqibin.starter.web.error.spi.ValidationErrorsFormatter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/// 全局 REST 异常处理器,使用共享平台错误解析管道呈现 RFC 7807 {@link ProblemDetail} 文档。
///
/// 此处理器负责:
///
/// - 捕获所有未处理的异常(`@ExceptionHandler(Exception.class)`)
///   - 使用 {@link ProblemDetailAdapter} 将异常转换为 {@link ProblemDetail}
///   - 处理验证异常({@link MethodArgumentNotValidException}),附加验证错误列表
///   - 掩码敏感字段(通过 {@link ValidationErrorsFormatter})
///   - 4xx 记 WARN、不带堆栈（按类名兜底分类出来的 4xx 带堆栈），5xx 记 ERROR、带堆栈；参数校验失败不记字段原始值
///   - 返回符合 RFC 7807 标准的 JSON 响应(Content-Type: application/problem+json)
///
/// **响应格式示例:**
///
/// ```java
/// {
///   "type": "about:blank",
///   "title": "Bad Request",
///   "status": 400,
///   "detail": "Validation failed for object='userRequest'",
///   "instance": "/api/users",
///   "errorCode": "ERR_VALIDATION_FAILED",
///   "path": "/api/users",
///   "errors": [
///     { "field": "email", "code": "Email", "message": "must be a valid email"
///   ]
/// ```
///
/// **优先级:** 比 {@link Ordered#HIGHEST_PRECEDENCE} 低一级。最高的位置留给安全 starter
/// 的处理器：它只把 Spring Security 的异常原样抛回安全过滤器，其余异常仍由本类兜底。
///
/// @see ProblemDetailAdapter
/// @see ValidationErrorsFormatter
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class GlobalRestExceptionHandler extends ResponseEntityExceptionHandler {

  /// 附加到问题详情载荷的验证错误的最大数量。
  private static final int MAX_VALIDATION_ERRORS = 100;

  /// 参数校验失败时返回给客户端的固定文案。异常自身的消息里带着字段原始值，不能外泄。
  private static final String VALIDATION_FAILED_DETAIL = "请求参数不合法";

  private final ProblemDetailAdapter problemDetailAdapter;
  private final ValidationErrorsFormatter validationErrorsFormatter;

  /// 构造全局异常处理器实例。
  ///
  /// @param problemDetailAdapter 问题详情适配器
  /// @param validationErrorsFormatter 验证错误格式化器
  public GlobalRestExceptionHandler(
      ProblemDetailAdapter problemDetailAdapter,
      ValidationErrorsFormatter validationErrorsFormatter) {
    this.problemDetailAdapter = problemDetailAdapter;
    this.validationErrorsFormatter = validationErrorsFormatter;
  }

  /// 后备处理器，将任何未捕获的异常转换为问题详情文档。
  ///
  /// 异常实现 {@link HasRetryAfter} 且剩余等待时间不为 `null` 时，加上 `Retry-After` 响应头。
  ///
  /// @param ex 未捕获的异常
  /// @param request HTTP 请求上下文
  /// @return 包含问题详情的响应实体
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleException(Exception ex, HttpServletRequest request) {
    ProblemDetailResponse response = problemDetailAdapter.adapt(ex, request);

    logExceptionHandled(response, ex);

    ResponseEntity.BodyBuilder builder =
        ResponseEntity.status(response.httpStatus())
            .contentType(MediaType.APPLICATION_PROBLEM_JSON);
    if (ex instanceof HasRetryAfter hasRetryAfter && hasRetryAfter.getRetryAfter() != null) {
      builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(hasRetryAfter.getRetryAfterSeconds()));
    }
    return builder.body(response.problemDetail());
  }

  /// 处理验证失败并将清理后的字段错误附加到响应载荷。
  ///
  /// `detail` 固定为「请求参数不合法」：异常消息里带着字段原始值（`rejected value [...]`），现有掩码拦不住。
  ///
  /// @param ex 验证异常
  /// @param headers HTTP 响应头
  /// @param status HTTP 状态码
  /// @param request Web 请求上下文
  /// @return 包含问题详情和验证错误列表的响应实体
  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      org.springframework.http.HttpHeaders headers,
      org.springframework.http.HttpStatusCode status,
      org.springframework.web.context.request.WebRequest request) {

    HttpServletRequest servletRequest = extractServletRequest(request);
    ProblemDetailResponse response = problemDetailAdapter.adapt(ex, servletRequest);

    List<ValidationError> errors = formatAndTruncateValidationErrors(ex);
    response.problemDetail().setDetail(VALIDATION_FAILED_DETAIL);
    response.problemDetail().setProperty(ErrorKeys.ERRORS, errors);

    logValidationExceptionHandled(response, errors);

    return ResponseEntity.status(response.httpStatus())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(response.problemDetail());
  }

  /// 从 Spring 的 WebRequest 包装器中提取 HttpServletRequest。
  ///
  /// @param request web 请求包装器
  /// @return servlet 请求或 null（如果不可用）
  private HttpServletRequest extractServletRequest(
      org.springframework.web.context.request.WebRequest request) {
    if (request
        instanceof org.springframework.web.context.request.ServletWebRequest servletWebRequest) {
      return servletWebRequest.getRequest();
    }
    return null;
  }

  /// 格式化验证错误并进行掩码，截断到允许的最大计数。
  ///
  /// @param ex 包含绑定结果的验证异常
  /// @return 格式化和截断的验证错误列表
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

  /// 记录通用异常处理。4xx 是调用方的问题，记 WARN、不带堆栈；5xx 记 ERROR、带堆栈。
  ///
  /// 按类名关键字或原因链兜底分类出来的 4xx（如 `IllegalStateException` 被归为 422）可能是服务端缺陷，
  /// 仍记 WARN，但保留堆栈。
  ///
  /// @param response 包含错误元数据的问题详情响应
  /// @param ex 被处理的异常
  private void logExceptionHandled(ProblemDetailResponse response, Exception ex) {
    Object path = extractPathFromProblemDetail(response.problemDetail());
    String code = response.errorResolution().errorCode().code();
    int status = response.httpStatus().value();
    if (response.httpStatus().is5xxServerError()) {
      log.error(
          "Exception handled: error code [{}], HTTP status {}, request path [{}], exception={}",
          code,
          status,
          path,
          ex.getClass().getSimpleName(),
          ex);
      return;
    }
    if (isInferredClassification(response.errorResolution().strategy())) {
      log.warn(
          "Exception handled: error code [{}], HTTP status {}, request path [{}], exception={}: {}",
          code,
          status,
          path,
          ex.getClass().getSimpleName(),
          ex.getMessage(),
          ex);
      return;
    }
    log.warn(
        "Exception handled: error code [{}], HTTP status {}, request path [{}], exception={}: {}",
        code,
        status,
        path,
        ex.getClass().getSimpleName(),
        ex.getMessage());
  }

  /// 解析结果是不是兜底推断出来的：按类名关键字或原因链猜的状态码，而不是异常自己声明的语义。
  ///
  /// @param strategy 解析策略
  /// @return 兜底推断出来的时为 `true`
  private static boolean isInferredClassification(ResolutionStrategy strategy) {
    return strategy == ResolutionStrategy.FALLBACK || strategy == ResolutionStrategy.CAUSE;
  }

  /// 记录参数校验失败。只记字段名和原因码，不记异常消息和堆栈：异常消息里带着字段原始值。
  ///
  /// @param response 问题详情响应
  /// @param errors 响应中包含的验证错误
  private void logValidationExceptionHandled(
      ProblemDetailResponse response, List<ValidationError> errors) {
    Object path = extractPathFromProblemDetail(response.problemDetail());
    List<String> fieldCodes =
        errors.stream().map(error -> error.field() + ":" + error.code()).toList();
    log.warn(
        "参数校验失败: error code [{}], HTTP status {}, request path [{}], errors={}",
        response.errorResolution().errorCode().code(),
        response.httpStatus().value(),
        path,
        fieldCodes);
  }

  /// 安全地从问题详情中提取路径属性。
  ///
  /// @param problemDetail 问题详情实例
  /// @return 路径值，不存在时返回 null
  private Object extractPathFromProblemDetail(ProblemDetail problemDetail) {
    return problemDetail.getProperties() == null
        ? null
        : problemDetail.getProperties().get(ErrorKeys.PATH);
  }
}
