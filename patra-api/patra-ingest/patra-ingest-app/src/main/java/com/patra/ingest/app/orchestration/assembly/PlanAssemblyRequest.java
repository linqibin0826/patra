package com.patra.ingest.app.orchestration.assembly;

import com.patra.ingest.domain.model.command.PlanTriggerNorm;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.value.PlannerWindow;
import com.patra.ingest.app.orchestration.expression.PlanExpressionDescriptor;
import java.util.Objects;

/**
 * Immutable request for plan assembly composed by the application service.
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
