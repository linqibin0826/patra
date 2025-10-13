package com.patra.registry.domain.exception.provenance;

import com.patra.registry.domain.exception.RegistryNotFound;

/**
 * Exception thrown when a Provenance is not found.
 *
 * <p>Thrown when the requested provenance does not exist or is unavailable in the current context.
 * The platform error handling maps this to HTTP 404 (REG-0404).
 *
 * @author linqibin
 * @since 0.1.0
 */
public class ProvenanceNotFoundException extends RegistryNotFound {

  /**
   * Creates an exception with a detail message.
   *
   * @param message detail message
   */
  public ProvenanceNotFoundException(String message) {
    super(message);
  }

  /**
   * Creates an exception with a detail message and root cause.
   *
   * @param message detail message
   * @param cause root cause
   */
  public ProvenanceNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}
