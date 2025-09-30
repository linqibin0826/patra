package com.patra.ingest.app.usecase.plan.assembler;

import com.patra.ingest.domain.model.vo.PlanTriggerNorm;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.PlannerWindow;
import com.patra.ingest.app.usecase.plan.expression.PlanExpressionDescriptor;
import java.util.Objects;

/**
 * 计划装配阶段输入（Application → Assembly）。
 * <p>
 * 将窗口解析、表达式构建、触发规范归一化与配置快照提取后的结果集中封装，供装配服务生成：
 * <ol>
 *   <li>Plan 聚合根（含配置 / 表达式快照签名）</li>
 *   <li>PlanSlice 聚合集合（由切片策略派生）</li>
 *   <li>Task 聚合集合（由切片再派生）</li>
 * </ol>
 * </p>
 * <h4>不变式</h4>
 * <ul>
 *   <li>{@code triggerNorm != null}</li>
 *   <li>{@code window != null}</li>
 *   <li>{@code configSnapshot != null}</li>
 *   <li>{@code planExpression != null}</li>
 * </ul>
 * <h4>线程安全</h4>
 * <p>record 不可变，可安全复用。</p>
 * <h4>扩展点</h4>
 * <p>后续可加入：租约信息 / 限流配置 / feature overrides；需保证向后兼容。</p>
 *
 * @param triggerNorm 触发规范（模式、优先级、用户窗口等）
 * @param window 计划窗口（UTC 半开区间）
 * @param configSnapshot 配置快照（用于 canonical / 哈希）
 * @param planExpression 计划表达式描述（expr + json + hash）
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
