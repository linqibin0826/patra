package com.patra.ingest.domain.model.aggregate;

import java.util.List;
import java.util.Objects;

/**
 * 计划编排产物，封装计划、切片、任务与状态。
 *
 * @param plan 计划聚合
 * @param slices 切片集合
 * @param tasks 任务集合
 * @param status 装配状态
 */
public record PlanAssembly(PlanAggregate plan,
                           List<PlanSliceAggregate> slices,
                           List<TaskAggregate> tasks,
                           PlanAssemblyStatus status) {
    public PlanAssembly {
        Objects.requireNonNull(plan, "plan不能为空");
        slices = slices == null ? List.of() : List.copyOf(slices);
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
        status = status == null ? PlanAssemblyStatus.READY : status;
    }

    public enum PlanAssemblyStatus {
        READY,
        PARTIAL,
        FAILED
    }
}
