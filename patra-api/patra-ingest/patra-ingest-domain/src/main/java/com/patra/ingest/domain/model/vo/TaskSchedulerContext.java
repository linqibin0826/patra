package com.patra.ingest.domain.model.vo;

/**
 * Scheduler context attached to a task.
 *
 * <p>Stores the scheduler batch identifier ({@code schedulerRunId}) and cross-component correlation
 * id.
 *
 * <ul>
 *   <li>{@code schedulerRunId}: shared id for a scheduler-triggered batch (used for aggregation)
 *   <li>{@code correlationId}: cross-system trace/log identifier
 * </ul>
 *
 * Immutable convenience methods prefixed with {@code with*} return new instances.
 */
public record TaskSchedulerContext(String schedulerRunId, String correlationId) {

  /** Create an empty scheduler context (both identifiers {@code null}). */
  public static TaskSchedulerContext empty() {
    return new TaskSchedulerContext(null, null);
  }

  /** Derive a new context with the provided {@code schedulerRunId}. */
  public TaskSchedulerContext withSchedulerRun(String runId) {
    return new TaskSchedulerContext(runId, correlationId);
  }

  /** Derive a new context with the provided {@code correlationId}. */
  public TaskSchedulerContext withCorrelation(String corrId) {
    return new TaskSchedulerContext(schedulerRunId, corrId);
  }

  /** Returns {@code true} when a scheduler run identifier is present. */
  public boolean hasSchedulerRun() {
    return schedulerRunId != null && !schedulerRunId.isBlank();
  }
}
