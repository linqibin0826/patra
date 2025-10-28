package com.patra.common.domain;

import java.io.Serializable;
import java.time.Instant;

/**
 * Marker interface for domain events.
 *
 * <p>Events should be immutable and may carry an occurrence timestamp and optional event
 * identifier.
 */
public interface DomainEvent extends Serializable {

  /** Timestamp representing when the event occurred (for ordering/auditing). */
  Instant occurredAt();
}
