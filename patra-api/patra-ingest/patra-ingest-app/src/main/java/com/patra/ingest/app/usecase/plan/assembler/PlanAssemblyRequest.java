package com.patra.ingest.app.usecase.plan.assembler;

import com.patra.ingest.domain.model.vo.PlanTriggerNorm;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.PlannerWindow;
import com.patra.ingest.app.usecase.plan.expression.PlanExpressionDescriptor;
import java.util.Objects;

/**
 * Input for plan assembly (Application → Assembly).
 * <p>
 * Packages results of window resolution, expression build, trigger normalization,
 * and configuration snapshot extraction for the assembler to produce:
 * <ol>
 *   <li>Plan aggregate root (with config/expression snapshot signatures)</li>
 *   <li>PlanSlice aggregates (derived by slicing strategy)</li>
 *   <li>Task aggregates (derived from slices)</li>
 * </ol>
 * </p>
 * <h4>Invariants</h4>
 * <ul>
 *   <li>{@code triggerNorm != null}</li>
 *   <li>{@code window != null}</li>
 *   <li>{@code configSnapshot != null}</li>
 *   <li>{@code planExpression != null}</li>
 * </ul>
 * <h4>Thread safety</h4>
 * <p>Record is immutable and safe for reuse.</p>
 * <h4>Extension</h4>
 * <p>May include: lease info / rate-limit config / feature overrides; must remain backward compatible.</p>
 *
 * @param triggerNorm trigger normalization (mode, priority, user window, etc.)
 * @param window plan window (UTC half-open interval)
 * @param configSnapshot config snapshot (for canonical/hash)
 * @param planExpression plan expression descriptor (expr + json + hash)
 */
public record PlanAssemblyRequest(
        PlanTriggerNorm triggerNorm,
        PlannerWindow window,
        ProvenanceConfigSnapshot configSnapshot,
        PlanExpressionDescriptor planExpression
) {
    public PlanAssemblyRequest {
        Objects.requireNonNull(triggerNorm, "triggerNorm must not be null");
        Objects.requireNonNull(window, "window must not be null");
        Objects.requireNonNull(configSnapshot, "configSnapshot must not be null");
        Objects.requireNonNull(planExpression, "planExpression must not be null");
    }
}
