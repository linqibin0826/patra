package com.patra.registry.adapter.rest.advice;

import com.patra.registry.adapter.rest.dto.ErrorResponse;
import com.patra.registry.domain.exception.DictionaryDomainException;
import com.patra.registry.domain.exception.DictionaryNotFoundException;
import com.patra.registry.domain.exception.DictionaryValidationException;
import com.patra.registry.domain.exception.DictionaryRepositoryException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Global exception handler for dictionary REST API endpoints.
 * Provides structured error responses with appropriate HTTP status codes and comprehensive logging.
 * Implements consistent error handling strategy across all dictionary API operations.
 * 
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@RestControllerAdvice
public class DictionaryExceptionHandler {
    
    /**
     * Handles dictionary not found exceptions with 404 status code.
     * Logs at INFO level since this is a normal business case (resource not found).
     * 
     * @param ex the dictionary not found exception
     * @return ResponseEntity with 404 status and error details
     */
    @ExceptionHandler(DictionaryNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDictionaryNotFoundException(DictionaryNotFoundException ex) {
        HttpServletRequest request = getCurrentRequest();
        String path = request != null ? request.getRequestURI() : "unknown";
        String traceId = getTraceId();
        
        log.info("Dictionary not found: message={}, typeCode={}, itemCode={}, path={}, traceId={}", 
                ex.getMessage(), ex.getTypeCode(), ex.getItemCode(), path, traceId);
        
        ErrorResponse errorResponse = new ErrorResponse(
            HttpStatus.NOT_FOUND.value(),
            HttpStatus.NOT_FOUND.getReasonPhrase(),
            ex.getMessage(),
            path,
            ex.getTypeCode(),
            ex.getItemCode()
        );
        errorResponse.setTraceId(traceId);
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }
    
    /**
     * Handles dictionary validation exceptions with 400 status code.
     * Logs at WARN level since this indicates business rule violations.
     * 
     * @param ex the dictionary validation exception
     * @return ResponseEntity with 400 status and validation error details
     */
    @ExceptionHandler(DictionaryValidationException.class)
    public ResponseEntity<ErrorResponse> handleDictionaryValidationException(DictionaryValidationException ex) {
        HttpServletRequest request = getCurrentRequest();
        String path = request != null ? request.getRequestURI() : "unknown";
        String traceId = getTraceId();
        
        log.warn("Dictionary validation failed: message={}, typeCode={}, itemCode={}, errors={}, path={}, traceId={}", 
                ex.getMessage(), ex.getTypeCode(), ex.getItemCode(), ex.getValidationErrors(), path, traceId);
        
        ErrorResponse errorResponse = new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            ex.getMessage(),
            path,
            ex.getValidationErrors()
        );
        errorResponse.setTypeCode(ex.getTypeCode());
        errorResponse.setItemCode(ex.getItemCode());
        errorResponse.setTraceId(traceId);
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
    
    /**
     * Handles general dictionary domain exceptions with 400 status code.
     * Logs at WARN level since these are business rule violations.
     * 
     * @param ex the dictionary domain exception
     * @return ResponseEntity with 400 status and error details
     */
    @ExceptionHandler(DictionaryDomainException.class)
    public ResponseEntity<ErrorResponse> handleDictionaryDomainException(DictionaryDomainException ex) {
        HttpServletRequest request = getCurrentRequest();
        String path = request != null ? request.getRequestURI() : "unknown";
        String traceId = getTraceId();
        
        log.warn("Dictionary domain error: message={}, typeCode={}, itemCode={}, path={}, traceId={}", 
                ex.getMessage(), ex.getTypeCode(), ex.getItemCode(), path, traceId);
        
        ErrorResponse errorResponse = new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            ex.getMessage(),
            path,
            ex.getTypeCode(),
            ex.getItemCode()
        );
        errorResponse.setTraceId(traceId);
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
    
    /**
     * Handles dictionary repository exceptions with 500 status code.
     * Logs at ERROR level since these are infrastructure/system failures.
     * 
     * @param ex the dictionary repository exception
     * @return ResponseEntity with 500 status and error details
     */
    @ExceptionHandler(DictionaryRepositoryException.class)
    public ResponseEntity<ErrorResponse> handleDictionaryRepositoryException(DictionaryRepositoryException ex) {
        HttpServletRequest request = getCurrentRequest();
        String path = request != null ? request.getRequestURI() : "unknown";
        String traceId = getTraceId();
        
        log.error("Dictionary repository error: message={}, operation={}, typeCode={}, itemCode={}, path={}, traceId={}", 
                 ex.getMessage(), ex.getOperation(), ex.getTypeCode(), ex.getItemCode(), path, traceId, ex);
        
        ErrorResponse errorResponse = new ErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
            "Dictionary service temporarily unavailable",
            path,
            ex.getTypeCode(),
            ex.getItemCode()
        );
        errorResponse.setTraceId(traceId);
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
    
    /**
     * Handles illegal argument exceptions with 400 status code.
     * Logs at WARN level since these are client input validation failures.
     * 
     * @param ex the illegal argument exception
     * @return ResponseEntity with 400 status and error details
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        HttpServletRequest request = getCurrentRequest();
        String path = request != null ? request.getRequestURI() : "unknown";
        String traceId = getTraceId();
        
        log.warn("Invalid request parameter: message={}, path={}, traceId={}", ex.getMessage(), path, traceId);
        
        ErrorResponse errorResponse = new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            "Invalid request parameter: " + ex.getMessage(),
            path
        );
        errorResponse.setTraceId(traceId);
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
    
    /**
     * Handles all other unexpected exceptions with 500 status code.
     * Logs at ERROR level since these are unexpected system failures.
     * 
     * @param ex the unexpected exception
     * @return ResponseEntity with 500 status and generic error message
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        HttpServletRequest request = getCurrentRequest();
        String path = request != null ? request.getRequestURI() : "unknown";
        String traceId = getTraceId();
        
        log.error("Unexpected error in dictionary API: message={}, type={}, path={}, traceId={}", 
                 ex.getMessage(), ex.getClass().getSimpleName(), path, traceId, ex);
        
        ErrorResponse errorResponse = new ErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
            "An unexpected error occurred",
            path
        );
        errorResponse.setTraceId(traceId);
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
    
    /**
     * Gets the current HTTP servlet request from the request context.
     * 
     * @return the current HTTP servlet request, or null if not available
     */
    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
    
    /**
     * Extracts the trace ID from the current request for correlation.
     * Attempts to get trace ID from SkyWalking or other tracing systems.
     * 
     * @return the trace ID if available, or "unknown" if not found
     */
    private String getTraceId() {
        try {
            HttpServletRequest request = getCurrentRequest();
            if (request != null) {
                // Try to get trace ID from common tracing headers
                String traceId = request.getHeader("X-Trace-Id");
                if (traceId != null && !traceId.isEmpty()) {
                    return traceId;
                }
                
                // Try SkyWalking trace ID
                traceId = request.getHeader("sw8-trace-id");
                if (traceId != null && !traceId.isEmpty()) {
                    return traceId;
                }
            }
            
            // Try to get from SkyWalking TraceContext if available
            // This would require SkyWalking dependency, so we'll use a fallback
            return "trace-" + System.currentTimeMillis();
            
        } catch (Exception e) {
            log.debug("Failed to extract trace ID: {}", e.getMessage());
            return "unknown";
        }
    }
}