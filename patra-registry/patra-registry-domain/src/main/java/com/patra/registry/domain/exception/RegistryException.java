package com.patra.registry.domain.exception;

import com.patra.common.error.DomainException;

/**
 * Base class for Registry domain exceptions.
 *
 * <p>Represents business rule violations inside the Registry domain and is intended to be handled
 * uniformly by the application layer. All Registry domain exceptions shall extend this class for
 * consistency.
 *
 * @author linqibin
 * @since 0.1.0
 */
public abstract class RegistryException extends DomainException {

  /**
   * Creates an exception with a message.
   *
   * @param message detail message
   */
  protected RegistryException(String message) {
    super(message);
  }

  /**
   * Creates an exception with a message and root cause.
   *
   * @param message detail message
   * @param cause root cause
   */
  protected RegistryException(String message, Throwable cause) {
    super(message, cause);
  }
}
