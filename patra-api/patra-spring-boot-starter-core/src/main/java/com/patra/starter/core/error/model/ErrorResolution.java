package com.patra.starter.core.error.model;

import com.patra.common.error.codes.ErrorCodeLike;

/**
 * Data structure representing the resolution of an exception to an error code and HTTP status.
 * Used by the error resolution algorithm to return both the error code and HTTP status.
 * 
 * @param errorCode the resolved error code, must not be null
 * @param httpStatus the resolved HTTP status code as integer
 * 
 * @author linqibin
 * @since 0.1.0
 */
public record ErrorResolution(
    ErrorCodeLike errorCode,
    int httpStatus
) {
    
    /**
     * Creates a new ErrorResolution.
     * 
     * @param errorCode the error code, must not be null
     * @param httpStatus the HTTP status code, must be valid HTTP status (100-599)
     * @throws IllegalArgumentException if errorCode is null or httpStatus is invalid
     */
    public ErrorResolution {
        if (errorCode == null) {
            throw new IllegalArgumentException("Error code must not be null");
        }
        if (httpStatus < 100 || httpStatus > 599) {
            throw new IllegalArgumentException("HTTP status must be between 100 and 599, got: " + httpStatus);
        }
    }
}