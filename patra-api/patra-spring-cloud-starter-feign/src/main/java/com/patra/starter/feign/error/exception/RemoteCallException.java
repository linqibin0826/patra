package com.patra.starter.feign.error.exception;

import com.patra.common.error.problem.ErrorKeys;
import lombok.Getter;
import org.springframework.http.ProblemDetail;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Exception representing errors from remote service calls via Feign clients.
 * This exception is designed for use in the adapter layer only and should not
 * cross boundaries into the application or domain layers.
 * 
 * Provides typed access to remote error information including error codes,
 * HTTP status, trace IDs, and additional extension fields from ProblemDetail responses.
 * 
 * @author linqibin
 * @since 0.1.0
 */
@Getter
public class RemoteCallException extends RuntimeException {
    
    /** The business error code from the remote service (may be null) */
    private final String errorCode;
    
    /** HTTP status code from the remote response */
    private final int httpStatus;
    
    /** Feign method key that triggered this exception */
    private final String methodKey;
    
    /** Trace ID for correlation across services (may be null) */
    private final String traceId;
    
    /** Additional extension fields from ProblemDetail */
    private final Map<String, Object> extensions;
    
    /**
     * Constructs a RemoteCallException from a ProblemDetail response.
     * This constructor extracts all relevant information from the ProblemDetail
     * including error code, trace ID, and extension fields.
     * 
     * @param problemDetail the ProblemDetail from the remote service response
     * @param methodKey the Feign method key that triggered this call
     */
    public RemoteCallException(ProblemDetail problemDetail, String methodKey) {
        super(problemDetail.getDetail());
        this.httpStatus = problemDetail.getStatus();
        this.methodKey = methodKey;
        
        // Extract error code from ProblemDetail properties
        Map<String, Object> properties = problemDetail.getProperties();
        this.errorCode = (String) properties.get(ErrorKeys.CODE);
        this.traceId = (String) properties.get(ErrorKeys.TRACE_ID);
        
        // Copy all extension fields for potential future use
        this.extensions = new HashMap<>(properties);
    }
    
    /**
     * Constructs a RemoteCallException for non-ProblemDetail responses.
     * Used when the remote service returns a non-standard error response
     * or when tolerant mode handles malformed responses.
     * 
     * @param httpStatus the HTTP status code from the response
     * @param message the error message (may be derived from response reason)
     * @param methodKey the Feign method key that triggered this call
     * @param traceId the trace ID if available from response headers (may be null)
     */
    public RemoteCallException(int httpStatus, String message, String methodKey, String traceId) {
        super(message);
        this.httpStatus = httpStatus;
        this.methodKey = methodKey;
        this.traceId = traceId;
        this.errorCode = null;
        this.extensions = Collections.emptyMap();
    }
    
    /**
     * Constructs a RemoteCallException with full parameter control.
     * Used for advanced scenarios where all fields need to be explicitly set.
     * 
     * @param errorCode the business error code (may be null)
     * @param httpStatus the HTTP status code
     * @param message the error message
     * @param methodKey the Feign method key
     * @param traceId the trace ID (may be null)
     * @param extensions additional extension fields (may be empty)
     */
    public RemoteCallException(String errorCode, int httpStatus, String message, 
                             String methodKey, String traceId, Map<String, Object> extensions) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.methodKey = methodKey;
        this.traceId = traceId;
        this.extensions = extensions != null ? new HashMap<>(extensions) : Collections.emptyMap();
    }
    
    /**
     * Checks if this exception has a business error code.
     * 
     * @return true if error code is present and not empty, false otherwise
     */
    public boolean hasErrorCode() {
        return errorCode != null && !errorCode.trim().isEmpty();
    }
    
    /**
     * Checks if this exception has a trace ID for correlation.
     * 
     * @return true if trace ID is present and not empty, false otherwise
     */
    public boolean hasTraceId() {
        return traceId != null && !traceId.trim().isEmpty();
    }
    
    /**
     * Gets an extension field value by key.
     * 
     * @param key the extension field key
     * @return the extension field value, or null if not present
     */
    public Object getExtension(String key) {
        return extensions.get(key);
    }
    
    /**
     * Gets an extension field value by key with type casting.
     * 
     * @param key the extension field key
     * @param type the expected type of the value
     * @param <T> the type parameter
     * @return the extension field value cast to the specified type, or null if not present or wrong type
     */
    @SuppressWarnings("unchecked")
    public <T> T getExtension(String key, Class<T> type) {
        Object value = extensions.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }
    
    /**
     * Returns a copy of all extension fields.
     * 
     * @return immutable map of extension fields
     */
    public Map<String, Object> getAllExtensions() {
        return Collections.unmodifiableMap(extensions);
    }
}