package com.patra.ingest.app.usecase.plan.command;

import com.patra.common.enums.Priority;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.enums.Scheduler;
import com.patra.ingest.domain.model.enums.TriggerType;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Plan orchestration command (Adapter → Application).
 *
 * <p>Built by a scheduler job or external caller; after parsing/defaulting in the Adapter, it is
 * passed to the application layer to:
 *
 * <ol>
 *   <li>Resolve the plan window (windowFrom/windowTo may be derived)
 *   <li>Build {@code PlanTriggerNorm} (mode, step, priority, etc.)
 *   <li>Drive plan assembly (expression, slicing strategy, task generation)
 * </ol>
 *
 * <h4>Field semantics & constraints</h4>
 *
 * <ul>
 *   <li><b>provenanceCode / operationCode</b>: define the business pair; required.
 *   <li><b>step</b>: ISO-8601 Duration (e.g., {@code PT1H}, {@code P1D}); optional and
 *       strategy-dependent.
 *   <li><b>windowFrom/windowTo</b>: half-open interval [from, to); both may be null for resolver to
 *       infer.
 *   <li><b>priority</b>: scheduling priority; defaults to {@link
 *       com.patra.common.enums.Priority#NORMAL}.
 *   <li><b>triggeredAt</b>: trigger time; defaults to now when null.
 *   <li><b>scheduler/schedulerJobId/schedulerLogId</b>: scheduler context for tracking; log id may
 *       be null.
 *   <li><b>triggerParams</b>: optional user params; must be Jackson-serializable; empty map equals
 *       null.
 * </ul>
 *
 * <h4>Invariants</h4>
 *
 * <ul>
 *   <li>{@code provenanceCode != null}
 *   <li>{@code operationCode != null}
 *   <li>{@code triggerType != null}
 *   <li>{@code scheduler != null}
 *   <li>{@code priority != null} (coerced in constructor)
 * </ul>
 *
 * <h4>Validation</h4>
 *
 * <ul>
 *   <li>Step syntax is validated by downstream strategy when applicable.
 *   <li>Window ordering is validated by the window resolver/validators.
 * </ul>
 *
 * <h4>Thread-safety</h4>
 *
 * <p>Record is immutable (no exposed mutable collection references) and safe to share across
 * threads.
 *
 * @param provenanceCode provenance code (required)
 * @param operationCode operation code (required)
 * @param step slice step (ISO-8601 duration; optional)
 * @param triggerType trigger type (required)
 * @param scheduler scheduler type (required)
 * @param schedulerJobId scheduler job id (nullable)
 * @param schedulerLogId scheduler log id (nullable)
 * @param windowFrom window start (nullable)
 * @param windowTo window end (nullable)
 * @param priority priority (defaults to NORMAL when null)
 * @param triggeredAt trigger time (nullable)
 * @param triggerParams extra trigger params (nullable)
 */
public record PlanIngestionCommand(
    ProvenanceCode provenanceCode,
    OperationCode operationCode,
    String step,
    TriggerType triggerType,
    Scheduler scheduler,
    String schedulerJobId,
    String schedulerLogId,
    Instant windowFrom,
    Instant windowTo,
    Priority priority,
    Instant triggeredAt,
    Map<String, Object> triggerParams) {
  public PlanIngestionCommand {
    Objects.requireNonNull(provenanceCode, "provenanceCode must not be null");
    Objects.requireNonNull(operationCode, "endpointName must not be null");
    Objects.requireNonNull(triggerType, "triggerType must not be null");
    Objects.requireNonNull(scheduler, "scheduler must not be null");
    priority = priority == null ? Priority.NORMAL : priority;
  }
}
