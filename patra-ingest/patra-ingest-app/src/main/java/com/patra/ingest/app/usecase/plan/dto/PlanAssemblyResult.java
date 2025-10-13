package com.patra.ingest.app.usecase.plan.dto;

import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import java.util.List;
import java.util.Objects;

/**
 * Application-layer DTO describing the outcome of plan assembly. It combines the aggregates
 * produced during assembly (plan, slices, tasks) with an overall status.
 *
 * <p>This is not a domain aggregate itself; it is a composite view passed between {@code
 * PlanAssembler} and {@code PlanIngestionOrchestrator}.
 *
 * @param plan assembled plan aggregate
 * @param slices assembled slice aggregates
 * @param tasks assembled task aggregates
 * @param status assembly status (READY/PARTIAL/FAILED)
 */
public record PlanAssemblyResult(
    PlanAggregate plan,
    List<PlanSliceAggregate> slices,
    List<TaskAggregate> tasks,
    AssemblyStatus status) {

  public PlanAssemblyResult {
    Objects.requireNonNull(plan, "plan must not be null");
    slices = slices == null ? List.of() : List.copyOf(slices);
    tasks = tasks == null ? List.of() : List.copyOf(tasks);
    status = status == null ? AssemblyStatus.READY : status;
  }

  /** Assembly status. */
  public enum AssemblyStatus {
    /** Assembly succeeded; all tasks are ready. */
    READY,
    /** Partially assembled; some artefacts are missing or deferred. */
    PARTIAL,
    /** Assembly failed. */
    FAILED
  }
}
