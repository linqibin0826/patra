package com.patra.ingest.domain.model.vo;

/**
 * Context metadata captured for a single task run.
 *
 * <p>Similar to {@link TaskSchedulerContext} but scoped to an individual run.
 *
 * <ul>
 *   <li>{@code schedulerRunId}: scheduler batch identifier
 *   <li>{@code correlationId}: cross-system correlation id
 * </ul>
 */
public record RunContext(String schedulerRunId, String correlationId) {

  /** Create an empty run context. */
  public static RunContext empty() {
    return new RunContext(null, null);
  }

  /** Derive a new context with the provided {@code schedulerRunId}. */
  public RunContext withSchedulerRun(String runId) {
    return new RunContext(runId, correlationId);
  }

  /** Derive a new context with the provided {@code correlationId}. */
  public RunContext withCorrelation(String corrId) {
    return new RunContext(schedulerRunId, corrId);
  }
}
