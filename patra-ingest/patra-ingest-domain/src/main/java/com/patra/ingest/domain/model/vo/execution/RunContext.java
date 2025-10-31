package com.patra.ingest.domain.model.vo.execution;

/**
 * Context metadata captured for a single task run.
 *
 * <p>Similar to {@link TaskSchedulerContext} but scoped to an individual run.
 *
 * <ul>
 *   <li>{@code correlationId}: cross-system correlation id for distributed tracing
 * </ul>
 */
public record RunContext(String correlationId) {

  /** Create an empty run context. */
  public static RunContext empty() {
    return new RunContext(null);
  }

  /** Derive a new context with the provided {@code correlationId}. */
  public RunContext withCorrelation(String corrId) {
    return new RunContext(corrId);
  }
}
