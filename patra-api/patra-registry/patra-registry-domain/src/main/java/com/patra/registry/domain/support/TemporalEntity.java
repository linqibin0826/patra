package com.patra.registry.domain.support;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;

/**
 * Represents an entity with temporal effectiveness constraints.
 *
 * <p>Entities implementing this interface have a validity period defined by {@code effectiveFrom}
 * and {@code effectiveTo} timestamps, supporting temporal queries and configuration slicing.
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface TemporalEntity {

  /**
   * Returns the inclusive start timestamp when this entity becomes effective.
   *
   * @return effective start instant, never null
   */
  Instant effectiveFrom();

  /**
   * Returns the exclusive end timestamp when this entity expires.
   *
   * @return effective end instant, null means open-ended (never expires)
   */
  Instant effectiveTo();

  /**
   * Checks whether this entity is effective at the given instant.
   *
   * <p>An entity is effective when the instant falls within its validity period: {@code
   * effectiveFrom <= instant < effectiveTo}.
   *
   * @param instant the time point to check, must not be null
   * @return true if the entity is effective at the given instant
   * @throws DomainValidationException if instant is null
   */
  default boolean isEffectiveAt(Instant instant) {
    DomainValidationException.nonNull(instant, "Instant");
    boolean afterStart = !instant.isBefore(effectiveFrom());
    boolean beforeEnd = effectiveTo() == null || instant.isBefore(effectiveTo());
    return afterStart && beforeEnd;
  }
}
