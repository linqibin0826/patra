package com.patra.ingest.domain.model.vo.plan;

/**
 * Scheduler context attached to a task.
 *
 * <p>Stores cross-component correlation id for distributed tracing.
 *
 * <ul>
 *   <li>{@code correlationId}: cross-system trace/log identifier
 * </ul>
 *
 * Immutable convenience methods prefixed with {@code with*} return new instances.
 */
public record TaskSchedulerContext(String correlationId) {

  /** Create an empty scheduler context (correlationId {@code null}). */
  public static TaskSchedulerContext empty() {
    return new TaskSchedulerContext(null);
  }

  /** Derive a new context with the provided {@code correlationId}. */
  public TaskSchedulerContext withCorrelation(String corrId) {
    return new TaskSchedulerContext(corrId);
  }
}
