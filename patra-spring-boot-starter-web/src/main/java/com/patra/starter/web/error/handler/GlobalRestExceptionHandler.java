package com.patra.starter.web.error.handler;

import com.patra.common.error.problem.ErrorKeys;
import com.patra.starter.web.error.adapter.ProblemDetailAdapter;
import com.patra.starter.web.error.adapter.model.ProblemDetailResponse;
import com.patra.starter.web.error.model.ValidationError;
import com.patra.starter.web.error.spi.ValidationErrorsFormatter;
import jakarta.servlet.http.HttpServletRequest;
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

import java.util.List;

/**
 * Global REST exception handler that renders RFC 7807 {@link ProblemDetail} documents
 * using the shared platform error resolution pipeline.
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalRestExceptionHandler extends ResponseEntityExceptionHandler {

    /** Maximum number of validation errors attached to the ProblemDetail payload. */
    private static final int MAX_VALIDATION_ERRORS = 100;

    private final ProblemDetailAdapter problemDetailAdapter;
    private final ValidationErrorsFormatter validationErrorsFormatter;

    public GlobalRestExceptionHandler(ProblemDetailAdapter problemDetailAdapter,
                                      ValidationErrorsFormatter validationErrorsFormatter) {
        this.problemDetailAdapter = problemDetailAdapter;
        this.validationErrorsFormatter = validationErrorsFormatter;
    }

    /**
     * Fallback handler that converts any uncaught exception into a ProblemDetail document.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleException(Exception ex, HttpServletRequest request) {
        ProblemDetailResponse response = problemDetailAdapter.adapt(ex, request);

        Object path = response.problemDetail().getProperties() == null
                ? null
                : response.problemDetail().getProperties().get(ErrorKeys.PATH);
        log.info("Exception handled: errorCode={} status={} path={}", 
                response.errorResolution().errorCode().code(),
                response.httpStatus().value(),
                path);

        return ResponseEntity
                .status(response.httpStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(response.problemDetail());
    }

    /**
     * Handles validation failures and appends sanitized field errors to the response payload.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            @NonNull MethodArgumentNotValidException ex,
            @NonNull org.springframework.http.HttpHeaders headers,
            @NonNull org.springframework.http.HttpStatusCode status,
            @NonNull org.springframework.web.context.request.WebRequest request) {

        HttpServletRequest servletRequest = null;
        if (request instanceof org.springframework.web.context.request.ServletWebRequest servletWebRequest) {
            servletRequest = servletWebRequest.getRequest();
        }

        ProblemDetailResponse response = problemDetailAdapter.adapt(ex, servletRequest);
        ProblemDetail problemDetail = response.problemDetail();

        List<ValidationError> errors = validationErrorsFormatter.formatWithMasking(ex.getBindingResult());
        if (errors.size() > MAX_VALIDATION_ERRORS) {
            log.warn("Validation errors truncated: total={}, included={}", errors.size(), MAX_VALIDATION_ERRORS);
            errors = errors.subList(0, MAX_VALIDATION_ERRORS);
        }
        problemDetail.setProperty(ErrorKeys.ERRORS, errors);

        Object path = problemDetail.getProperties() == null
                ? null
                : problemDetail.getProperties().get(ErrorKeys.PATH);
        log.info("Validation exception handled: errorCode={} validationErrors={} path={} status={}",
                response.errorResolution().errorCode().code(),
                errors.size(),
                path,
                response.httpStatus().value());

        return ResponseEntity
                .status(response.httpStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }
}
