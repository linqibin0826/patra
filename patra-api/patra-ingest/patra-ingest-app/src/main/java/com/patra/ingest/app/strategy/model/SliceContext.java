package com.patra.ingest.app.strategy.model;

import com.patra.ingest.app.model.PlanBusinessExpr;
import com.patra.ingest.domain.model.command.PlanTriggerNorm;
import com.patra.ingest.domain.model.value.PlannerWindow;
import java.util.Objects;

/**
 * 切片策略上下文。
 */
public record SliceContext(PlanTriggerNorm norm,
                           PlannerWindow window,
                           PlanBusinessExpr planExpr) {
    public SliceContext {
        Objects.requireNonNull(norm, "norm不能为空");
        Objects.requireNonNull(window, "window不能为空");
        Objects.requireNonNull(planExpr, "planExpr不能为空");
    }
}
