package com.patra.registry.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;
import java.util.Set;

/**
 * Base exception class for Registry quota/rate limit/capacity exceeded semantics.
 *
 * <p>Represents operations that fail due to exceeding quotas, rate limits, or capacity constraints
 * (e.g., count or size limits).
 *
 * @author linqibin
 * @since 0.1.0
 */
public abstract class RegistryQuotaExceeded extends RegistryException implements HasErrorTraits {

  /**
   * Creates an exception with a message.
   *
   * @param message detail message
   */
  protected RegistryQuotaExceeded(String message) {
    super(message);
  }

  /**
   * Creates an exception with a message and root cause.
   *
   * @param message detail message
   * @param cause root cause
   */
  protected RegistryQuotaExceeded(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Returns the error traits for this exception (always QUOTA_EXCEEDED).
   *
   * @return set containing QUOTA_EXCEEDED trait
   */
  @Override
  public Set<ErrorTrait> getErrorTraits() {
    return Set.of(ErrorTrait.QUOTA_EXCEEDED);
  }
}
