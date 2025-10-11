package com.patra.starter.web.error.adapter;

import com.patra.starter.web.error.adapter.model.ProblemDetailResponse;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Adapter that translates exceptions into consistent {@code ProblemDetail} responses.
 */
public interface ProblemDetailAdapter {

    /**
     * Convert the supplied exception into a {@link ProblemDetailResponse}.
     *
     * @param exception exception being processed
     * @param request   HTTP request context; may be {@code null} in non-servlet flows
     * @return resolved ProblemDetail metadata and HTTP status
     */
    ProblemDetailResponse adapt(Throwable exception, HttpServletRequest request);
}
