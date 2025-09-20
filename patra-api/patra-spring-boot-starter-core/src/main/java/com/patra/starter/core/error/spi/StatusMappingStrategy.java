package com.patra.starter.core.error.spi;

import com.patra.common.error.codes.ErrorCodeLike;

/**
 * Strategy interface for mapping error codes to HTTP status codes.
 * Returns int instead of HttpStatus to avoid spring-web dependency in core module.
 * 
 * @author linqibin
 * @since 0.1.0
 */
public interface StatusMappingStrategy {
    
    /**
     * Maps an error code and exception to an HTTP status code.
     * 
     * @param errorCode the error code to map, must not be null
     * @param exception the exception that triggered the error, may be null
     * @return HTTP status code as integer (e.g., 404, 500)
     */
    int mapToHttpStatus(ErrorCodeLike errorCode, Throwable exception);
}