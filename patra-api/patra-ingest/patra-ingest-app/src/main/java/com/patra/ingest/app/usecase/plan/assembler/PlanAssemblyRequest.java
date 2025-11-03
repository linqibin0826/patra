package com.patra.ingest.app.usecase.plan.assembler;

import com.patra.ingest.app.usecase.plan.expression.PlanExpressionDescriptor;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.plan.PlanTriggerNorm;
import com.patra.ingest.domain.model.vo.plan.PlannerWindow;
import java.util.Objects;

/**
 * Plan 组装输入(应用层 → 组装器)
 *
 * <p>打包窗口解析、表达式构建、触发器标准化和配置快照提取的结果,供组装器生成:
 *
 * <ol>
 *   <li>Plan 聚合根(带配置/表达式快照签名)
 *   <li>PlanSlice 聚合(由切片策略派生)
 *   <li>Task 聚合(由切片派生)
 * </ol>
 *
 * <h4>不变式</h4>
 *
 * <ul>
 *   <li>{@code triggerNorm != null}
 *   <li>{@code window != null}
 *   <li>{@code configSnapshot != null}
 *   <li>{@code planExpression != null}
 * </ul>
 *
 * <h4>线程安全</h4>
 *
 * <p>Record 不可变,可安全重用。
 *
 * <h4>扩展性</h4>
 *
 * <p>可包含:租约信息 / 限流配置 / 特性覆盖;必须保持向后兼容。
 *
 * @param triggerNorm 触发器标准化(模式、优先级、用户窗口等)
 * @param window Plan 窗口(UTC 半开区间)
 * @param configSnapshot 配置快照(用于规范化/哈希)
 * @param planExpression Plan 表达式描述符(expr + json + hash)
 */
public record PlanAssemblyRequest(
    PlanTriggerNorm triggerNorm,
    PlannerWindow window,
    ProvenanceConfigSnapshot configSnapshot,
    PlanExpressionDescriptor planExpression) {
  public PlanAssemblyRequest {
    Objects.requireNonNull(triggerNorm, "triggerNorm 不能为 null");
    Objects.requireNonNull(window, "window 不能为 null");
    Objects.requireNonNull(configSnapshot, "configSnapshot 不能为 null");
    Objects.requireNonNull(planExpression, "planExpression 不能为 null");
  }
}
