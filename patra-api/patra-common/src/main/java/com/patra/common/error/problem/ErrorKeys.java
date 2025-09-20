package com.patra.common.error.problem;

/**
 * Constants for standard ProblemDetail extension field keys used across
 * the error handling system. These keys ensure consistent field naming
 * in RFC 7807 ProblemDetail responses.
 * 
 * @author linqibin
 * @since 0.1.0
 */
public final class ErrorKeys {
    
    /** Error code field key for business error codes */
    public static final String CODE = "code";
    
    /** Trace ID field key for distributed tracing correlation */
    public static final String TRACE_ID = "traceId";
    
    /** Request path field key for the original request path */
    public static final String PATH = "path";
    
    /** Timestamp field key for when the error occurred */
    public static final String TIMESTAMP = "timestamp";
    
    /** Validation errors field key for detailed validation error information */
    public static final String ERRORS = "errors";
    
    private ErrorKeys() {
        // Utility class - prevent instantiation
    }
}