package com.patra.ingest.app.orchestration.slice.model;

import com.patra.ingest.app.orchestration.expression.PlanExpressionDescriptor;
import com.patra.ingest.domain.model.command.PlanTriggerNorm;
import com.patra.ingest.domain.model.value.PlannerWindow;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import java.util.Objects;

/**
 * 切片策略上下文。
 * <p>
 * 聚合计划触发规范、计划窗口、业务表达式以及来源配置快照，作为切片策略执行的输入。
 * 该上下文由应用层在编排阶段统一构建，保证策略执行时信息完备、语义一致。
 * </p>
 *
 * @param norm            触发规范，包含计划模式、步长等信息
 * @param window          计划窗口，统一采用 UTC 半开区间
 * @param planExpression  计划表达式描述，含 Expr、快照 JSON 与哈希
 * @param configSnapshot  来源配置快照，可选（部分测试场景为空）
 *
 * @author linqibin
 * @since 0.1.0
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
