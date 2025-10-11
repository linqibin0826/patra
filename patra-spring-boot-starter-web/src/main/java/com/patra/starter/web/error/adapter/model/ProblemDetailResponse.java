package com.patra.starter.web.error.adapter.model;

import com.patra.starter.core.error.model.ErrorResolution;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Holder for the resolved {@link ProblemDetail}, HTTP status, and error metadata.
 */
public record ProblemDetailResponse(
        ProblemDetail problemDetail,
        HttpStatus httpStatus,
        ErrorResolution errorResolution
) {
}
