package com.patra.starter.core.error.spi;

import com.patra.common.error.codes.ErrorCodeLike;

import java.util.Optional;

/**
 * SPI interface for providing fine-grained error code mappings.
 * Allows services to override default error resolution for specific exceptions.
 * 
 * @author linqibin
 * @since 0.1.0
 */
public interface ErrorMappingContributor {
    
    /**
     * Maps an exception to a specific error code.
     * 
     * @param exception the exception to map, must not be null
     * @return Optional containing the error code if this contributor handles the exception, empty otherwise
     */
    Optional<ErrorCodeLike> mapException(Throwable exception);
}