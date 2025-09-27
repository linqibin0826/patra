package com.patra.ingest.app.orchestration.assembly;

import com.patra.ingest.domain.model.command.PlanTriggerNorm;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.value.PlannerWindow;
import com.patra.ingest.app.orchestration.expression.PlanExpressionDescriptor;
import java.util.Objects;

/**
 * 应用层构造的计划装配请求，聚合触发规范、窗口、配置与表达式。
 *
 * @param triggerNorm 触发规范
 * @param window 计划窗口
 * @param configSnapshot 配置快照
 * @param planExpression 计划表达式描述
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
