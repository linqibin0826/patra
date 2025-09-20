package com.patra.common.error;

import com.patra.common.error.codes.ErrorCodeLike;

/**
 * Application layer exception that carries business error codes.
 * This exception is used in the application layer to wrap domain exceptions
 * or represent application-specific errors with structured error codes.
 * 
 * The embedded ErrorCodeLike is used by the error resolution algorithm
 * to determine appropriate HTTP status codes and error responses.
 * 
 * @author linqibin
 * @since 0.1.0
 */
public class ApplicationException extends RuntimeException {
    
    /** The business error code associated with this exception */
    private final ErrorCodeLike errorCode;
    
    /**
     * Constructs a new application exception with the specified error code and message.
     * 
     * @param errorCode the business error code, must not be null
     * @param message the detail message explaining the exception
     * @throws IllegalArgumentException if errorCode is null
     */
    public ApplicationException(ErrorCodeLike errorCode, String message) {
        super(message);
        if (errorCode == null) {
            throw new IllegalArgumentException("ErrorCode cannot be null");
        }
        this.errorCode = errorCode;
    }
    
    /**
     * Constructs a new application exception with the specified error code, message, and cause.
     * 
     * @param errorCode the business error code, must not be null
     * @param message the detail message explaining the exception
     * @param cause the cause of this exception
     * @throws IllegalArgumentException if errorCode is null
     */
    public ApplicationException(ErrorCodeLike errorCode, String message, Throwable cause) {
        super(message, cause);
        if (errorCode == null) {
            throw new IllegalArgumentException("ErrorCode cannot be null");
        }
        this.errorCode = errorCode;
    }
    
    /**
     * Returns the business error code associated with this exception.
     * 
     * @return the error code, never null
     */
    public ErrorCodeLike getErrorCode() {
        return errorCode;
    }
}