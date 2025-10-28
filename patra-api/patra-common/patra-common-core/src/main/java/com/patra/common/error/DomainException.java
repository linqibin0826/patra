package com.patra.common.error;

/**
 * Base type for domain-layer exceptions.
 *
 * <p>Offers a framework-agnostic abstraction for domain-specific failures so the domain layer
 * remains decoupled from Spring and other infrastructure concerns.
 *
 * <p>Domain exceptions should extend this class to keep business rules clearly separated from
 * technical implementation details.
 *
 * @author linqibin
 * @since 0.1.0
 */
public abstract class DomainException extends RuntimeException {

  /** Creates a domain exception with the provided message. */
  protected DomainException(String message) {
    super(message);
  }

  /** Creates a domain exception with the provided message and root cause. */
  protected DomainException(String message, Throwable cause) {
    super(message, cause);
  }
}
