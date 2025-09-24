package com.patra.ingest.app.strategy;

import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.model.command.PlanTriggerNorm;
import com.patra.ingest.domain.model.expr.ExprPlanArtifacts;
import com.patra.ingest.domain.model.value.PlannerWindow;

import java.util.List;

public interface SliceStrategy {
    String code();
    List<PlanSliceAggregate> slice(PlanTriggerNorm norm,
                                   PlannerWindow window,
                                   ExprPlanArtifacts exprArtifacts);
}
