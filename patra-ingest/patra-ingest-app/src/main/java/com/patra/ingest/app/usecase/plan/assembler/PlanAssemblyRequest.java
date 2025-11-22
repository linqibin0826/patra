package com.patra.ingest.app.usecase.plan.assembler;

import com.patra.ingest.app.usecase.plan.expression.PlanExpressionDescriptor;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.plan.PlanTriggerNorm;
import com.patra.ingest.domain.model.vo.plan.PlannerWindow;
import java.util.Objects;

/// Plan 组装输入(应用层 → 组装器)
///
/// 打包窗口解析、表达式构建、触发器标准化和配置快照提取的结果,供组装器生成:
///
/// #### 不变式
///
/// - `triggerNorm != null`
///   - `window != null`
///   - `configSnapshot != null`
///   - `planExpression != null`
///
/// #### 线程安全
///
/// Record 不可变,可安全重用。
///
/// #### 扩展性
///
/// 可包含:租约信息 / 限流配置 / 特性覆盖;必须保持向后兼容。
///
/// @param triggerNorm 触发器标准化(模式、优先级、用户窗口等)
/// @param window Plan 窗口(UTC 半开区间)
/// @param configSnapshot 配置快照(用于规范化/哈希)
/// @param planExpression Plan 表达式描述符(expr + json + hash)
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
