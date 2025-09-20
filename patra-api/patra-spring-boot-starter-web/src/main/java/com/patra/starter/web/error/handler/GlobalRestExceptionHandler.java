package com.patra.starter.web.error.handler;

import com.patra.common.error.problem.ErrorKeys;
import com.patra.starter.core.error.model.ErrorResolution;
import com.patra.starter.core.error.service.ErrorResolutionService;
import com.patra.starter.web.error.builder.ProblemDetailBuilder;
import com.patra.starter.web.error.model.ValidationError;
import com.patra.starter.web.error.spi.ValidationErrorsFormatter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

/**
 * Global REST exception handler that converts all exceptions to RFC 7807 ProblemDetail responses.
 * Extends ResponseEntityExceptionHandler to handle Spring MVC exceptions and provides unified
 * error handling across all REST controllers.
 * 
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalRestExceptionHandler extends ResponseEntityExceptionHandler {
    
    /** Maximum number of validation errors to include in response */
    private static final int MAX_VALIDATION_ERRORS = 100;
    
    private final ErrorResolutionService errorResolutionService;
    private final ProblemDetailBuilder problemDetailBuilder;
    private final ValidationErrorsFormatter validationErrorsFormatter;
    
    public GlobalRestExceptionHandler(
            ErrorResolutionService errorResolutionService,
            ProblemDetailBuilder problemDetailBuilder,
            ValidationErrorsFormatter validationErrorsFormatter) {
        this.errorResolutionService = errorResolutionService;
        this.problemDetailBuilder = problemDetailBuilder;
        this.validationErrorsFormatter = validationErrorsFormatter;
    }
    
    /**
     * Handles all general exceptions using the error resolution algorithm.
     * 
     * @param ex the exception to handle, must not be null
     * @param request the HTTP servlet request, must not be null
     * @return ResponseEntity with ProblemDetail body and appropriate HTTP status
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleException(Exception ex, HttpServletRequest request) {
        log.debug("Handling exception: {}", ex.getClass().getSimpleName(), ex);
        
        ErrorResolution resolution = errorResolutionService.resolve(ex);
        ProblemDetail problemDetail = problemDetailBuilder.build(resolution, ex, request);
        
        // Convert int status to HttpStatus with fallback to 500
        HttpStatus httpStatus = convertToHttpStatus(resolution.httpStatus());
        
        log.info("Exception handled: errorCode={}, httpStatus={}, path={}", 
                resolution.errorCode().code(), httpStatus.value(), 
                problemDetail.getProperties().get(ErrorKeys.PATH));
        
        return ResponseEntity
            .status(httpStatus)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problemDetail);
    }
    
    /**
     * Handles validation exceptions with detailed validation error information.
     * 
     * @param ex the method argument not valid exception, must not be null
     * @param request the HTTP servlet request, must not be null
     * @return ResponseEntity with ProblemDetail body including validation errors array
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        
        log.debug("Handling validation exception: errorCount={}", ex.getBindingResult().getErrorCount());
        
        ErrorResolution resolution = errorResolutionService.resolve(ex);
        ProblemDetail problemDetail = problemDetailBuilder.build(resolution, ex, request);
        
        // Add validation errors array with sensitive data masking
        List<ValidationError> errors = validationErrorsFormatter.formatWithMasking(ex.getBindingResult());
        
        // Limit errors array size to prevent oversized responses
        if (errors.size() > MAX_VALIDATION_ERRORS) {
            log.warn("Validation errors truncated: total={}, included={}", 
                    errors.size(), MAX_VALIDATION_ERRORS);
            errors = errors.subList(0, MAX_VALIDATION_ERRORS);
        }
        
        problemDetail.setProperty(ErrorKeys.ERRORS, errors);
        
        log.info("Validation exception handled: errorCode={}, validationErrors={}, path={}", 
                resolution.errorCode().code(), errors.size(), 
                problemDetail.getProperties().get(ErrorKeys.PATH));
        
        return ResponseEntity
            .status(HttpStatus.UNPROCESSABLE_ENTITY)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problemDetail);
    }
    
    /**
     * Converts int HTTP status to HttpStatus with fallback to 500.
     * 
     * @param status the HTTP status code as int
     * @return the corresponding HttpStatus, defaults to INTERNAL_SERVER_ERROR for invalid codes
     */
    private HttpStatus convertToHttpStatus(int status) {
        try {
            return HttpStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid HTTP status code: {}, falling back to 500", status);
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }
}