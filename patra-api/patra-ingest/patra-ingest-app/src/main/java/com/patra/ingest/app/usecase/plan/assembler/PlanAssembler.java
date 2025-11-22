package com.patra.ingest.app.usecase.plan.assembler;

import com.patra.ingest.app.usecase.plan.dto.PlanAssemblyResult;

/// 计划组装服务契约。
/// 
/// 接受完全准备好的 {@link PlanAssemblyRequest}（窗口已解析、表达式已编译、配置快照已收集），返回包含 Plan + PlanSlice[] + Task[]
/// 聚合根及 READY / FAILED 状态的 {@link PlanAssemblyResult}：
/// 
/// - READY: 至少生成了一个切片和一个任务
///   - FAILED: 切片或任务缺失；计划标记为失败
/// 
/// #### Idempotency
/// 
/// Enforced upstream via the plan key (provenance + operation + endpoint + window). This
/// interface does not guarantee idempotent responses on repeated invocation.
/// 
/// #### Error handling
/// 
/// Implementations may throw runtime exceptions for invalid configuration or missing policies;
/// callers should translate them into domain exceptions.
public interface PlanAssembler {

  /// Executes assembly flow: create Plan, derive slices, and create tasks.
/// 
/// @param request assembly request (non-null)
/// @return assembly result (with status)
  PlanAssemblyResult assemble(PlanAssemblyRequest request);
}
