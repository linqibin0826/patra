package com.patra.starter.feign.error.util;

import com.patra.starter.feign.error.exception.RemoteCallException;

/**
 * Utility class providing convenience methods for analyzing RemoteCallException instances.
 * Offers semantic checks for common HTTP status codes and error patterns to simplify
 * error handling in adapter layer code.
 * 
 * @author linqibin
 * @since 0.1.0
 */
public final class RemoteErrorHelper {
    
    private RemoteErrorHelper() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Checks if the remote error represents a "Not Found" condition.
     * Matches both HTTP 404 status and error codes ending with "-0404".
     * 
     * @param ex the RemoteCallException to check
     * @return true if this represents a not found error, false otherwise
     */
    public static boolean isNotFound(RemoteCallException ex) {
        return ex.getHttpStatus() == 404 || 
               (ex.getErrorCode() != null && ex.getErrorCode().endsWith("-0404"));
    }
    
    /**
     * Checks if the remote error represents a "Conflict" condition.
     * Matches both HTTP 409 status and error codes ending with "-0409".
     * 
     * @param ex the RemoteCallException to check
     * @return true if this represents a conflict error, false otherwise
     */
    public static boolean isConflict(RemoteCallException ex) {
        return ex.getHttpStatus() == 409 || 
               (ex.getErrorCode() != null && ex.getErrorCode().endsWith("-0409"));
    }
    
    /**
     * Checks if the remote error represents an "Unauthorized" condition.
     * Matches both HTTP 401 status and error codes ending with "-0401".
     * 
     * @param ex the RemoteCallException to check
     * @return true if this represents an unauthorized error, false otherwise
     */
    public static boolean isUnauthorized(RemoteCallException ex) {
        return ex.getHttpStatus() == 401 || 
               (ex.getErrorCode() != null && ex.getErrorCode().endsWith("-0401"));
    }
    
    /**
     * Checks if the remote error represents a "Forbidden" condition.
     * Matches both HTTP 403 status and error codes ending with "-0403".
     * 
     * @param ex the RemoteCallException to check
     * @return true if this represents a forbidden error, false otherwise
     */
    public static boolean isForbidden(RemoteCallException ex) {
        return ex.getHttpStatus() == 403 || 
               (ex.getErrorCode() != null && ex.getErrorCode().endsWith("-0403"));
    }
    
    /**
     * Checks if the remote error represents an "Unprocessable Entity" condition.
     * Matches both HTTP 422 status and error codes ending with "-0422".
     * 
     * @param ex the RemoteCallException to check
     * @return true if this represents an unprocessable entity error, false otherwise
     */
    public static boolean isUnprocessableEntity(RemoteCallException ex) {
        return ex.getHttpStatus() == 422 || 
               (ex.getErrorCode() != null && ex.getErrorCode().endsWith("-0422"));
    }
    
    /**
     * Checks if the remote error represents a "Too Many Requests" condition.
     * Matches both HTTP 429 status and error codes ending with "-0429".
     * 
     * @param ex the RemoteCallException to check
     * @return true if this represents a rate limiting error, false otherwise
     */
    public static boolean isTooManyRequests(RemoteCallException ex) {
        return ex.getHttpStatus() == 429 || 
               (ex.getErrorCode() != null && ex.getErrorCode().endsWith("-0429"));
    }
    
    /**
     * Checks if the remote error represents a client error (4xx status codes).
     * 
     * @param ex the RemoteCallException to check
     * @return true if this is a client error (400-499), false otherwise
     */
    public static boolean isClientError(RemoteCallException ex) {
        return ex.getHttpStatus() >= 400 && ex.getHttpStatus() < 500;
    }
    
    /**
     * Checks if the remote error represents a server error (5xx status codes).
     * 
     * @param ex the RemoteCallException to check
     * @return true if this is a server error (500-599), false otherwise
     */
    public static boolean isServerError(RemoteCallException ex) {
        return ex.getHttpStatus() >= 500 && ex.getHttpStatus() < 600;
    }
    
    /**
     * Checks if the remote error has a specific error code.
     * Performs exact string matching on the error code.
     * 
     * @param ex the RemoteCallException to check
     * @param errorCode the expected error code
     * @return true if the error codes match exactly, false otherwise
     */
    public static boolean is(RemoteCallException ex, String errorCode) {
        return errorCode != null && errorCode.equals(ex.getErrorCode());
    }
    
    /**
     * Checks if the remote error has any business error code.
     * 
     * @param ex the RemoteCallException to check
     * @return true if error code is present and not empty, false otherwise
     */
    public static boolean hasErrorCode(RemoteCallException ex) {
        return ex.hasErrorCode();
    }
    
    /**
     * Checks if the remote error has a trace ID for correlation.
     * 
     * @param ex the RemoteCallException to check
     * @return true if trace ID is present and not empty, false otherwise
     */
    public static boolean hasTraceId(RemoteCallException ex) {
        return ex.hasTraceId();
    }
    
    /**
     * Checks if the remote error matches any of the provided error codes.
     * 
     * @param ex the RemoteCallException to check
     * @param errorCodes the error codes to match against
     * @return true if the error code matches any of the provided codes, false otherwise
     */
    public static boolean isAnyOf(RemoteCallException ex, String... errorCodes) {
        if (errorCodes == null || errorCodes.length == 0) {
            return false;
        }
        
        String actualCode = ex.getErrorCode();
        if (actualCode == null) {
            return false;
        }
        
        for (String code : errorCodes) {
            if (actualCode.equals(code)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Checks if the remote error represents a temporary failure that might be retryable.
     * Includes server errors (5xx) and specific client errors like 429 (Too Many Requests).
     * 
     * @param ex the RemoteCallException to check
     * @return true if this error might be retryable, false otherwise
     */
    public static boolean isRetryable(RemoteCallException ex) {
        int status = ex.getHttpStatus();
        return isServerError(ex) || 
               status == 429 || // Too Many Requests
               status == 408 || // Request Timeout
               status == 503 || // Service Unavailable
               status == 504;   // Gateway Timeout
    }
}