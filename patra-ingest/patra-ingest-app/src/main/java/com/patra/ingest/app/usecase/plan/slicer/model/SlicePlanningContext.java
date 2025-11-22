package com.patra.ingest.app.usecase.plan.slicer.model;

import com.patra.ingest.app.usecase.plan.expression.PlanExpressionDescriptor;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.plan.PlanTriggerNorm;
import com.patra.ingest.domain.model.vo.plan.PlannerWindow;
import java.util.Objects;

/// 切片策略执行上下文(应用层·输入模型)
///
/// 在 Plan 组装期间集中构建,以确保进入 `SlicePlanner` 时:
///
/// - 触发规范(模式/步长/操作)已标准化;
///   - 规划窗口已最终确定(UTC 半开区间语义 [from, to));
///   - 业务表达式已编译(带有 JSON 快照和哈希用于幂等和调试);
///   - 溯源快照(如果有)与表达式逻辑匹配。
///
/// #### 语义说明
///
/// - **norm**: 非空;封装触发模式(例如 HARVEST/BACKFILL/UPDATE)、切片步长和优先级。
///   - **window**: 非空;半开区间;当 window.to() 为 null 时表示开放上界。
///   - **planExpression**: 非空;其哈希用于后续切片签名/幂等性。
///   - **configSnapshot**: 可为空;某些测试或临时场景可能省略溯源配置(策略应优雅处理 null)。
///
/// #### 不变式
///
/// - `norm != null`
///   - `window != null`
///   - `planExpression != null`
///
/// #### 线程安全
///
/// Record 不可变;不暴露可变集合;线程安全。
///
/// #### 扩展性
///
/// 如果将来需要租约/限流信息,可添加新字段并保持 record 兼容性(或迁移为类)。
///
/// @param norm 触发规范,包括模式、步长等(必需)
/// @param window 规划窗口,使用 UTC 半开区间语义(必需)
/// @param planExpression 已编译的表达式描述符,包含 Expr、快照 JSON 和哈希(必需)
/// @param configSnapshot 溯源/数据源配置快照;可选(策略应在为 null 时回退到默认值)
/// @author linqibin
/// @since 0.1.0
public record SlicePlanningContext(
    PlanTriggerNorm norm,
    PlannerWindow window,
    PlanExpressionDescriptor planExpression,
    ProvenanceConfigSnapshot configSnapshot) {
  public SlicePlanningContext {
    Objects.requireNonNull(norm, "norm 不能为 null");
    Objects.requireNonNull(window, "window 不能为 null");
    Objects.requireNonNull(planExpression, "planExpression 不能为 null");
    // configSnapshot 可以为 null(某些测试或调用可能不提供它)
  }
}
