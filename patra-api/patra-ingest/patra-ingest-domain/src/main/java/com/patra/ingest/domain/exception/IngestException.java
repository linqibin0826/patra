package com.patra.ingest.domain.exception;

import com.patra.common.error.DomainException;

/**
 * Base class for ingestion domain exceptions.
 *
 * <p>Purpose: encapsulates explicit domain failures together with persistence or dependency issues
 * that are tightly coupled to the domain. This allows application and adapter layers to:
 *
 * <ul>
 *   <li>Differentiate retryable and non-retryable errors (combined with {@code ErrorTrait} of
 *       subclasses).
 *   <li>Enforce consistent logging and reduce alert noise by matching on the inheritance hierarchy.
 *   <li>Produce granular metrics within outbox flows or scheduler callbacks.
 * </ul>
 *
 * <p>Usage guidelines:
 *
 * <ul>
 *   <li>All new ingestion domain exceptions should extend this class and implement {@code
 *       HasErrorTraits} when error traits are exposed.
 *   <li>Avoid throwing generic {@code RuntimeException}; convert to a meaningful subclass instead.
 *   <li>Include key context (plan key, provenance, operation, window) in logs to speed up
 *       investigations.
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
public abstract class IngestException extends DomainException {

  /**
   * Construct the exception with a message.
   *
   * @param message detailed message
   */
  protected IngestException(String message) {
    super(message);
  }

  /**
   * Construct the exception with a message and cause.
   *
   * @param message detailed message
   * @param cause root cause
   */
  protected IngestException(String message, Throwable cause) {
    super(message, cause);
  }
}
