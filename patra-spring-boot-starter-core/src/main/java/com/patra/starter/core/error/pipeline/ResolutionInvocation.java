package com.patra.starter.core.error.pipeline;

import com.patra.starter.core.error.model.ErrorResolution;

/**
 * Represents the execution chain within the error-resolution pipeline and allows interceptors to
 * delegate control to the next step.
 */
@FunctionalInterface
public interface ResolutionInvocation {

  /**
   * Proceeds to the next step in the interceptor chain.
   *
   * @param exception the exception currently being resolved
   * @return the resolved error representation
   */
  ErrorResolution proceed(Throwable exception);
}
