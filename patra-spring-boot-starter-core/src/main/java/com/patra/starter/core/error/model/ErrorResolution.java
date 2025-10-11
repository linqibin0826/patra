package com.patra.starter.core.error.model;

import com.patra.common.error.codes.ErrorCodeLike;

/**
 * Result of resolving an exception: combines the business error code and HTTP status.
 *
 * @param errorCode resolved business error code (never {@code null})
 * @param httpStatus resolved HTTP status code (100–599)
 */
public record ErrorResolution(
    ErrorCodeLike errorCode,
    int httpStatus
) {

    /**
     * Validates constructor arguments.
     *
     * @param errorCode resolved error code (never {@code null})
     * @param httpStatus HTTP status in the range 100–599
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
