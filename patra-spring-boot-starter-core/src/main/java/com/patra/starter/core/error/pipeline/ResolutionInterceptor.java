package com.patra.starter.core.error.pipeline;

import com.patra.starter.core.error.model.ErrorResolution;

/**
 * Interceptor in the error-resolution pipeline, enabling cross-cutting concerns such as circuit breaking and metrics.
 */
public interface ResolutionInterceptor {

    /**
     * Applies interception logic and delegates to the next step in the pipeline.
     *
     * @param exception exception being resolved
     * @param invocation pipeline invocation
     * @return resolved error
     */
    ErrorResolution intercept(Throwable exception, ResolutionInvocation invocation);
}
