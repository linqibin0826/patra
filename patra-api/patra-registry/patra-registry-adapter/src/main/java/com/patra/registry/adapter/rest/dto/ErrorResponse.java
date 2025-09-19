package com.patra.registry.adapter.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Structured error response DTO for REST API error handling.
 * Provides consistent error information format for all dictionary API endpoints
 * with appropriate HTTP status codes and detailed error context.
 * 
 * @author linqibin
 * @since 0.1.0
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    
    /** Timestamp when the error occurred */
    private LocalDateTime timestamp;
    
    /** HTTP status code of the error response */
    private int status;
    
    /** HTTP status reason phrase */
    private String error;
    
    /** Main error message describing what went wrong */
    private String message;
    
    /** Request path where the error occurred */
    private String path;
    
    /** Unique trace ID for request correlation and debugging */
    private String traceId;
    
    /** Dictionary type code associated with the error, if applicable */
    private String typeCode;
    
    /** Dictionary item code associated with the error, if applicable */
    private String itemCode;
    
    /** List of detailed validation errors for complex validation failures */
    private List<String> validationErrors;
    
    /**
     * Creates a new error response with basic information.
     * 
     * @param status HTTP status code
     * @param error HTTP status reason phrase
     * @param message error message
     * @param path request path
     */
    public ErrorResponse(int status, String error, String message, String path) {
        this.timestamp = LocalDateTime.now();
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
    }
    
    /**
     * Creates a new error response with dictionary context.
     * 
     * @param status HTTP status code
     * @param error HTTP status reason phrase
     * @param message error message
     * @param path request path
     * @param typeCode dictionary type code
     * @param itemCode dictionary item code
     */
    public ErrorResponse(int status, String error, String message, String path, 
                        String typeCode, String itemCode) {
        this(status, error, message, path);
        this.typeCode = typeCode;
        this.itemCode = itemCode;
    }
    
    /**
     * Creates a new error response with validation errors.
     * 
     * @param status HTTP status code
     * @param error HTTP status reason phrase
     * @param message error message
     * @param path request path
     * @param validationErrors list of validation error messages
     */
    public ErrorResponse(int status, String error, String message, String path, 
                        List<String> validationErrors) {
        this(status, error, message, path);
        this.validationErrors = validationErrors;
    }
}