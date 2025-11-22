package com.patra.ingest.app.usecase.plan.dto;

import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import java.util.List;
import java.util.Objects;

/// 应用层 DTO，描述计划组装结果。将组装期间生成的聚合根（Plan、Slice、Task）与总体状态组合。
/// 
/// 这不是领域聚合根本身；而是在 `PlanAssembler` 和 `PlanIngestionOrchestrator` 之间传递的复合视图。
/// 
/// @param plan 已组装的计划聚合根
/// @param slices 已组装的切片聚合根
/// @param tasks 已组装的任务聚合根
/// @param status 组装状态 (READY/PARTIAL/FAILED)
public record PlanAssemblyResult(
    PlanAggregate plan,
    List<PlanSliceAggregate> slices,
    List<TaskAggregate> tasks,
    AssemblyStatus status) {

  public PlanAssemblyResult {
    Objects.requireNonNull(plan, "plan must not be null");
    slices = slices == null ? List.of() : List.copyOf(slices);
    tasks = tasks == null ? List.of() : List.copyOf(tasks);
    status = status == null ? AssemblyStatus.READY : status;
  }

  /// Assembly status.
  public enum AssemblyStatus {
    /// Assembly succeeded; all tasks are ready.
    READY,
    /// Partially assembled; some artefacts are missing or deferred.
    PARTIAL,
    /// Assembly failed.
    FAILED
  }
}
