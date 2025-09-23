package com.patra.ingest.domain.model.command;

import com.patra.ingest.domain.model.expr.ExprPlanArtifacts;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.value.PlannerWindow;

import java.util.Objects;

/**
 * 计划编排所需的组合指令。
 */
public record PlanBlueprintCommand(
        PlanTriggerNorm triggerNorm,
        PlannerWindow window,
        ProvenanceConfigSnapshot configSnapshot,
        ExprPlanArtifacts exprArtifacts
) {
    public PlanBlueprintCommand {
        Objects.requireNonNull(triggerNorm, "triggerNorm不能为空");
        Objects.requireNonNull(window, "window不能为空");
        Objects.requireNonNull(configSnapshot, "configSnapshot不能为空");
        Objects.requireNonNull(exprArtifacts, "exprArtifacts不能为空");
    }
}
