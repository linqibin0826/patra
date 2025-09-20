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
 * 全局 REST 异常处理器：统一转换为 RFC 7807 的 {@link org.springframework.http.ProblemDetail} 响应。
 *
 * <p>继承 {@link org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler}
 * 处理 Spring MVC 层异常，为所有 REST 控制器提供一致的错误输出格式。</p>
 *
 * @author linqibin
 * @since 0.1.0
 * @see com.patra.starter.web.error.builder.ProblemDetailBuilder
 * @see com.patra.starter.core.error.service.ErrorResolutionService
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalRestExceptionHandler extends ResponseEntityExceptionHandler {
    
    /** 响应中包含的校验错误上限 */
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
     * 处理一般异常，按错误解析算法转换为 ProblemDetail。
     *
     * @param ex 异常对象
     * @param request HTTP 请求
     * @return 含 ProblemDetail 的响应实体
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
     * 处理参数校验异常，并返回包含详细校验错误的 ProblemDetail。
     *
     * @param ex 参数校验异常
     * @param request HTTP 请求
     * @return 含校验错误数组的 ProblemDetail 响应
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
     * 将 int 状态码安全转换为 HttpStatus，非法值回退为 500。
     *
     * @param status 整型状态码
     * @return 对应的 HttpStatus
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
