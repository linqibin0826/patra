package com.patra.registry.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;
import java.util.Set;

/**
 * Base exception class for Registry rule violation semantics.
 *
 * <p>Represents operations that violate business rules, validation constraints, or data integrity
 * (e.g., invalid format, constraint conflicts).
 *
 * @author linqibin
 * @since 0.1.0
 */
public abstract class RegistryRuleViolation extends RegistryException implements HasErrorTraits {

  /**
   * Creates an exception with a message.
   *
   * @param message detail message
   */
  protected RegistryRuleViolation(String message) {
    super(message);
  }

  /**
   * Creates an exception with a message and root cause.
   *
   * @param message detail message
   * @param cause root cause
   */
  protected RegistryRuleViolation(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Returns the error traits for this exception (always RULE_VIOLATION).
   *
   * @return set containing RULE_VIOLATION trait
   */
  @Override
  public Set<ErrorTrait> getErrorTraits() {
    return Set.of(ErrorTrait.RULE_VIOLATION);
  }
}
