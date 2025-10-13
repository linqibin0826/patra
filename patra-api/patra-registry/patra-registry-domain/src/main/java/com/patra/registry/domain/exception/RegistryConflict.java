package com.patra.registry.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;
import java.util.Set;

/**
 * Base exception class for Registry resource conflicts.
 *
 * <p>Represents operations that cannot complete due to conflicts with existing resources or rules
 * (e.g., duplicate names, version conflicts).
 *
 * @author linqibin
 * @since 0.1.0
 */
public abstract class RegistryConflict extends RegistryException implements HasErrorTraits {

  /**
   * Creates an exception with a message.
   *
   * @param message detail message
   */
  protected RegistryConflict(String message) {
    super(message);
  }

  /**
   * Creates an exception with a message and root cause.
   *
   * @param message detail message
   * @param cause root cause
   */
  protected RegistryConflict(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Returns the error traits for this exception (always CONFLICT).
   *
   * @return set containing CONFLICT trait
   */
  @Override
  public Set<ErrorTrait> getErrorTraits() {
    return Set.of(ErrorTrait.CONFLICT);
  }
}
