package com.patra.ingest.app.usecase.plan.assembler;

import com.patra.ingest.app.usecase.plan.dto.PlanAssemblyResult;

/**
 * Plan assembly service contract.
 *
 * <p>Accepts a fully prepared {@link PlanAssemblyRequest} (window resolved, expression compiled,
 * configuration snapshot collected) and returns a {@link PlanAssemblyResult} containing Plan +
 * PlanSlice[] + Task[] aggregates alongside a READY / FAILED status:
 *
 * <ul>
 *   <li>READY: at least one slice and one task were produced.
 *   <li>FAILED: slices or tasks are missing; the plan is marked failed.
 * </ul>
 *
 * <h4>Idempotency</h4>
 *
 * <p>Enforced upstream via the plan key (provenance + operation + endpoint + window). This
 * interface does not guarantee idempotent responses on repeated invocation.
 *
 * <h4>Error handling</h4>
 *
 * <p>Implementations may throw runtime exceptions for invalid configuration or missing policies;
 * callers should translate them into domain exceptions.
 */
public interface PlanAssembler {

  /**
   * Executes assembly flow: create Plan, derive slices, and create tasks.
   *
   * @param request assembly request (non-null)
   * @return assembly result (with status)
   */
  PlanAssemblyResult assemble(PlanAssemblyRequest request);
}
