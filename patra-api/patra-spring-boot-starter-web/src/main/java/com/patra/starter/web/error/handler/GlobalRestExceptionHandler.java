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
 * Global REST exception handler that renders RFC 7807 {@link ProblemDetail} documents using the
 * shared platform error resolution pipeline.
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalRestExceptionHandler extends ResponseEntityExceptionHandler {

  /** Maximum number of validation errors attached to the ProblemDetail payload. */
  private static final int MAX_VALIDATION_ERRORS = 100;

  private final ProblemDetailAdapter problemDetailAdapter;
  private final ValidationErrorsFormatter validationErrorsFormatter;

  public GlobalRestExceptionHandler(
      ProblemDetailAdapter problemDetailAdapter,
      ValidationErrorsFormatter validationErrorsFormatter) {
    this.problemDetailAdapter = problemDetailAdapter;
    this.validationErrorsFormatter = validationErrorsFormatter;
  }

  /** Fallback handler that converts any uncaught exception into a ProblemDetail document. */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleException(Exception ex, HttpServletRequest request) {
    ProblemDetailResponse response = problemDetailAdapter.adapt(ex, request);

    logExceptionHandled(response, ex);

    return ResponseEntity.status(response.httpStatus())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(response.problemDetail());
  }

  /** Handles validation failures and appends sanitized field errors to the response payload. */
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
   * Extracts HttpServletRequest from Spring's WebRequest wrapper.
   *
   * @param request web request wrapper
   * @return servlet request or null if not available
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
   * Formats validation errors with masking and truncates to maximum allowed count.
   *
   * @param ex validation exception containing binding results
   * @return formatted and truncated validation error list
   */
  private List<ValidationError> formatAndTruncateValidationErrors(
      MethodArgumentNotValidException ex) {
    List<ValidationError> errors =
        validationErrorsFormatter.formatWithMasking(ex.getBindingResult());

    if (errors.size() > MAX_VALIDATION_ERRORS) {
      log.warn(
          "Validation errors exceeded maximum limit: total={}, truncated to {}",
          errors.size(),
          MAX_VALIDATION_ERRORS);
      return errors.subList(0, MAX_VALIDATION_ERRORS);
    }

    return errors;
  }

  /**
   * Logs general exception handling with error code, status, request path, and full stack trace.
   *
   * @param response problem detail response containing error metadata
   * @param ex the exception that was handled
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
   * Logs validation exception handling with validation error count, metadata, and stack trace.
   *
   * @param response problem detail response
   * @param errors validation errors included in response
   * @param ex the validation exception that was handled
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
   * Safely extracts path property from ProblemDetail.
   *
   * @param problemDetail problem detail instance
   * @return path value or null if not present
   */
  private Object extractPathFromProblemDetail(ProblemDetail problemDetail) {
    return problemDetail.getProperties() == null
        ? null
        : problemDetail.getProperties().get(ErrorKeys.PATH);
  }
}
