package com.patra.ingest.app.strategy;

import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.model.command.PlanTriggerNorm;
import com.patra.ingest.domain.model.expr.ExprPlanArtifacts;
import com.patra.ingest.domain.model.value.PlannerWindow;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SingleSliceStrategy implements SliceStrategy {
    @Override
    public String code() { return "SINGLE"; }

    @Override
    public List<PlanSliceAggregate> slice(PlanTriggerNorm norm,
                                          PlannerWindow window,
                                          ExprPlanArtifacts exprArtifacts) {
        String exprHash = exprArtifacts.sliceTemplates().isEmpty() ? exprArtifacts.exprProtoHash() : exprArtifacts.sliceTemplates().getFirst().exprHash();
        String exprSnapshot = exprArtifacts.sliceTemplates().isEmpty() ? exprArtifacts.exprProtoSnapshotJson() : exprArtifacts.sliceTemplates().getFirst().exprSnapshotJson();
        return List.of(PlanSliceAggregate.create(
                null,
                norm.provenanceCode().getCode(),
                1,
                "SINGLE",
                "{\"type\":\"SINGLE\"}",
                exprHash,
                exprSnapshot));
    }
}
