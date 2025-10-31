package com.patra.ingest.domain.model.vo.execution;

import java.util.Map;

/**
 * Immutable key/value parameters supplied to a task.
 *
 * <p>Represents context, configuration, or hints required during execution. Invariant: the internal
 * map is always non-null and immutable.
 */
public record TaskParams(Map<String, Object> values) {
  public TaskParams {
    values = values == null ? Map.of() : Map.copyOf(values);
  }

  /** Returns {@code true} when no parameters are present. */
  public boolean isEmpty() {
    return values.isEmpty();
  }
}
