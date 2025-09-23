package com.patra.ingest.domain.model.expr;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 表达式编译产物：计划和切片执行所需的快照与模板。
 */
public record ExprPlanArtifacts(String exprProtoHash,
                                String exprProtoSnapshotJson,
                                Map<String, Object> planLevelParameters,
                                List<ExprSliceTemplate> sliceTemplates) {
    public ExprPlanArtifacts {
        Objects.requireNonNull(exprProtoHash, "exprProtoHash不能为空");
        sliceTemplates = sliceTemplates == null ? List.of() : List.copyOf(sliceTemplates);
        planLevelParameters = planLevelParameters == null ? Map.of() : Map.copyOf(planLevelParameters);
    }
}
