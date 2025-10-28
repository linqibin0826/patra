package com.patra.common.domain;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.Getter;

/**
 * Abstract base class for aggregate roots.
 *
 * <p>Constraints and conventions:
 *
 * <ul>
 *   <li>Depends solely on the JDK; the domain layer stays framework-free.
 *   <li>State changes must occur through aggregate behaviors to preserve invariants.
 *   <li>Domain events remain attached to the aggregate and are pulled by the application layer for
 *       publication (e.g., outbox or message bus).
 * </ul>
 *
 * @param <ID> aggregate identifier type (value object or wrapped primitive)
 */
public abstract class AggregateRoot<ID> implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Aggregate identifier assigned by the repository during initial persistence.
   *
   * <p>Getter returns {@code null} while the aggregate has not been persisted.
   */
  @Getter private ID id;

  /**
   * Optional optimistic-lock version maintained by the infrastructure layer.
   *
   * <p>Exposed primarily for read-only checks within the domain.
   */
  @Getter private long version;

  /** Pending domain events awaiting collection by the application layer. */
  private final transient List<DomainEvent> domainEvents = new ArrayList<>();

  protected AggregateRoot() {}

  protected AggregateRoot(ID id) {
    this.id = id;
  }

  /**
   * Assigns the identifier when rebuilding or persisting for the first time. Intended for
   * repository use only.
   */
  public void assignId(ID id) {
    this.id = Objects.requireNonNull(id, "aggregate id must not be null");
  }

  /** Sets the optimistic-lock version. Infrastructure should call this when persisting updates. */
  public void assignVersion(long version) {
    if (version < 0) {
      throw new IllegalArgumentException("version must be >= 0");
    }
    this.version = version;
  }

  /** Indicates whether the aggregate has not yet been persisted. */
  public boolean isTransient() {
    return this.id == null;
  }

  /** Registers a domain event produced by aggregate behavior after state changes. */
  protected void addDomainEvent(DomainEvent event) {
    if (event == null) return;
    domainEvents.add(event);
  }

  /**
   * Drains and clears staged domain events. The application layer should call this inside the
   * transaction boundary before publishing to the outbox.
   */
  public List<DomainEvent> pullDomainEvents() {
    if (domainEvents.isEmpty()) {
      return Collections.emptyList();
    }
    List<DomainEvent> snapshot = List.copyOf(domainEvents);
    domainEvents.clear();
    return snapshot;
  }

  /** Returns an immutable view of staged domain events (for debugging or tests). */
  public List<DomainEvent> peekDomainEvents() {
    return Collections.unmodifiableList(domainEvents);
  }

  /**
   * Hook for domain invariant checks. Override to validate state after critical transitions and
   * throw {@link IllegalStateException} when invariants are violated.
   */
  protected void assertInvariants() {
    // Default no-op; subclasses should enforce invariants such as state-machine
    // validity or value-object consistency.
  }

  /* ========== Optional helper to assign default timestamps to events ========== */

  /** Supplies the current time when an event timestamp is missing. */
  protected static Instant nowIfNull(Instant t) {
    return (t == null) ? Instant.now() : t;
  }
}
