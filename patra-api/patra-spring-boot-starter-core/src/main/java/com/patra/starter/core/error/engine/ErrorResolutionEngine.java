package com.patra.starter.core.error.engine;

import com.patra.starter.core.error.model.ErrorResolution;

/**
 * Engine interface that converts arbitrary exceptions into the platform's unified error
 * representation.
 */
public interface ErrorResolutionEngine {

  /**
   * Resolves the supplied exception into a structured error.
   *
   * @param exception exception to resolve (never {@code null})
   * @return resolved error result
   */
  ErrorResolution resolve(Throwable exception);
}
