package com.patra.starter.core.error.spi;

import com.patra.common.error.codes.ErrorCodeLike;

import java.util.Optional;

/**
 * SPI for supplying fine-grained error-code mappings.
 * <p>Allows services to override the default resolution logic for specific exceptions.</p>
 */
public interface ErrorMappingContributor {

    /**
     * Provides an error code for the supplied exception if this contributor can handle it.
     *
     * @param exception exception to map (never {@code null})
     * @return optional error code; empty if the contributor does not apply
     */
    Optional<ErrorCodeLike> mapException(Throwable exception);
}
