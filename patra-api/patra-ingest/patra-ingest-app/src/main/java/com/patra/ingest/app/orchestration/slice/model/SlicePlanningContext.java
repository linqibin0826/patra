package com.patra.ingest.app.orchestration.slice.model;

import com.patra.ingest.app.orchestration.expression.PlanExpressionDescriptor;
import com.patra.ingest.domain.model.command.PlanTriggerNorm;
import com.patra.ingest.domain.model.value.PlannerWindow;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import java.util.Objects;

/**
 * 切片策略上下文。
 */
public record SlicePlanningContext(PlanTriggerNorm norm,
                                   PlannerWindow window,
                                   PlanExpressionDescriptor planExpression,
                                   ProvenanceConfigSnapshot configSnapshot) {
    public SlicePlanningContext {
        Objects.requireNonNull(norm, "norm不能为空");
        Objects.requireNonNull(window, "window不能为空");
        Objects.requireNonNull(planExpression, "planExpression不能为空");
        // configSnapshot 允许为空（某些测试或调用场景下可能未提供）
    }
}
