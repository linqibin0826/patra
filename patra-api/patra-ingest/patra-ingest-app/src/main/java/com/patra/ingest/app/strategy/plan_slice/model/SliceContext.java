package com.patra.ingest.app.strategy.plan_slice.model;

import com.patra.ingest.app.model.PlanBusinessExpr;
import com.patra.ingest.domain.model.command.PlanTriggerNorm;
import com.patra.ingest.domain.model.value.PlannerWindow;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import java.util.Objects;

/**
 * 切片策略上下文。
 */
public record SliceContext(PlanTriggerNorm norm,
                           PlannerWindow window,
                           PlanBusinessExpr planExpr,
                           ProvenanceConfigSnapshot configSnapshot) {
    public SliceContext {
        Objects.requireNonNull(norm, "norm不能为空");
        Objects.requireNonNull(window, "window不能为空");
        Objects.requireNonNull(planExpr, "planExpr不能为空");
        // configSnapshot 允许为空（某些测试或调用场景下可能未提供）
    }
}
