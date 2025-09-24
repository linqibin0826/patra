package com.patra.ingest.app.strategy;

import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.model.command.PlanTriggerNorm;
import com.patra.ingest.domain.model.expr.ExprPlanArtifacts;
import com.patra.ingest.domain.model.value.PlannerWindow;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class TimeSliceStrategy implements SliceStrategy {

    private static final Duration DEFAULT_STEP = Duration.ofHours(1);

    @Override
    public String code() {
        return "TIME";
    }

    @Override
    public List<PlanSliceAggregate> slice(PlanTriggerNorm norm,
                                          PlannerWindow window,
                                          ExprPlanArtifacts exprArtifacts) {
        List<PlanSliceAggregate> result = new ArrayList<>();
        if (window == null || window.from() == null || window.to() == null) return result;
        Instant from = window.from();
        Instant to = window.to();
        if (!from.isBefore(to)) return result;
        Duration step = DEFAULT_STEP;
        if (norm.step() != null && !norm.step().isBlank()) {
            try {
                step = Duration.parse(norm.step());
            } catch (Exception e) {
                // 保留默认步长
            }
        }
        Instant cursor = from;
        int index = 1;
        while (cursor.isBefore(to)) {
            Instant upper = cursor.plus(step);
            if (upper.isAfter(to)) upper = to;
            String specJson = "{\"type\":\"TIME\",\"from\":\"" + cursor + "\",\"to\":\"" + upper + "\"}";
            String exprHash = exprArtifacts.sliceTemplates().isEmpty() ? exprArtifacts.exprProtoHash() : exprArtifacts.sliceTemplates().getFirst().exprHash();
            String exprSnapshot = exprArtifacts.sliceTemplates().isEmpty() ? exprArtifacts.exprProtoSnapshotJson() : exprArtifacts.sliceTemplates().getFirst().exprSnapshotJson();
            result.add(PlanSliceAggregate.create(
                    null,
                    norm.provenanceCode().getCode(),
                    index,
                    specJson, // 暂用 specJson 作为签名原料（后续 canonical hash）
                    specJson,
                    exprHash,
                    exprSnapshot));
            cursor = upper;
            index++;
        }
        return result;
    }
}
