package com.patra.common.error;

import com.patra.common.error.codes.ErrorCodeLike;

/**
 * Application-layer exception that carries a structured business error code.
 *
 * <p>Used to wrap domain exceptions or represent application errors enriched with
 * an {@link ErrorCodeLike}. The embedded code guides the error-resolution
 * algorithm when determining the HTTP status and serialized response.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public class ApplicationException extends RuntimeException {
    
    /** Business error code associated with this exception. */
    private final ErrorCodeLike errorCode;
    
    /** Creates an exception with the provided error code and message. */
    public ApplicationException(ErrorCodeLike errorCode, String message) {
        super(message);
        if (errorCode == null) {
            throw new IllegalArgumentException("ErrorCode cannot be null");
        }
        this.errorCode = errorCode;
    }
    
    /** Creates an exception with the provided error code, message, and root cause. */
    public ApplicationException(ErrorCodeLike errorCode, String message, Throwable cause) {
        super(message, cause);
        if (errorCode == null) {
            throw new IllegalArgumentException("ErrorCode cannot be null");
        }
        this.errorCode = errorCode;
    }
    
    /** Returns the associated business error code. */
    public ErrorCodeLike getErrorCode() {
        return errorCode;
    }
}
