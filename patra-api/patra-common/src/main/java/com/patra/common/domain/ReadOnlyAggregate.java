package com.patra.common.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import lombok.Getter;

/**
 * Abstract base class for read-only aggregates.
 *
 * <p>Designed for the CQRS query side, it manages identifiers without bringing in write-side
 * concerns such as domain events or versioning.
 *
 * <p>Constraints:
 *
 * <ul>
 *   <li>Depends solely on the JDK; the domain layer remains framework-free.
 *   <li>Focuses on data retrieval and validation of business rules.
 *   <li>Does not support state mutations or event publication.
 *   <li>Well suited for configuration, dictionary, and view-style aggregates.
 * </ul>
 *
 * @param <ID> aggregate identifier type (value object or wrapped primitive)
 */
public abstract class ReadOnlyAggregate<ID> implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** Aggregate identifier. */
  @Getter private final ID id;

  protected ReadOnlyAggregate(ID id) {
    this.id = Objects.requireNonNull(id, "aggregate id must not be null");
  }

  protected ReadOnlyAggregate() {
    this.id = null;
  }

  /** Indicates whether the aggregate is transient (identifier not assigned). */
  public boolean isTransient() {
    return this.id == null;
  }

  /**
   * Hook for invariant checks. Override to validate state during construction or query-time
   * operations and throw {@link IllegalStateException} when invariants do not hold.
   */
  protected void assertInvariants() {
    // Default no-op; subclasses should enforce data integrity and business rules.
  }

  /** Equality based on the aggregate identifier. */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ReadOnlyAggregate<?> that = (ReadOnlyAggregate<?>) o;
    return Objects.equals(id, that.id);
  }

  /** Hash code derived from the aggregate identifier. */
  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }

  /** Human-readable representation including the identifier. */
  @Override
  public String toString() {
    return getClass().getSimpleName() + "{" + "id=" + id + '}';
  }
}
