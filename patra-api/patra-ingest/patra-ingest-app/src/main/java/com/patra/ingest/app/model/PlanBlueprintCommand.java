package com.patra.ingest.app.model;

import com.patra.ingest.domain.model.command.PlanTriggerNorm;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.value.PlannerWindow;
import java.util.Objects;

/**
 * 计划编排蓝图命令（应用层）。
 */
public record PlanBlueprintCommand(
        PlanTriggerNorm triggerNorm,
        PlannerWindow window,
        ProvenanceConfigSnapshot configSnapshot,
        PlanBusinessExpr planBusinessExpr
) {
    public PlanBlueprintCommand {
        Objects.requireNonNull(triggerNorm, "triggerNorm不能为空");
        Objects.requireNonNull(window, "window不能为空");
        Objects.requireNonNull(configSnapshot, "configSnapshot不能为空");
        Objects.requireNonNull(planBusinessExpr, "planBusinessExpr不能为空");
    }
}
