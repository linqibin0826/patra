package com.patra.ingest.app.usecase.plan.dto;

import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;

import java.util.List;
import java.util.Objects;

/**
 * 计划装配结果，封装装配过程产生的聚合集合与状态。
 * <p>
 * 这是应用层的 DTO，用于在 PlanAssembler 和 PlanIngestionOrchestrator 之间传递装配结果。
 * 它不是领域层的聚合根，而是多个聚合根的组合视图。
 * </p>
 *
 * @param plan   计划聚合根
 * @param slices 切片聚合根集合
 * @param tasks  任务聚合根集合
 * @param status 装配状态（READY/PARTIAL/FAILED）
 * @author linqibin
 * @since 0.1.0
 */
public record PlanAssemblyResult(PlanAggregate plan,
                                 List<PlanSliceAggregate> slices,
                                 List<TaskAggregate> tasks,
                                 AssemblyStatus status) {

    public PlanAssemblyResult {
        Objects.requireNonNull(plan, "plan不能为空");
        slices = slices == null ? List.of() : List.copyOf(slices);
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
        status = status == null ? AssemblyStatus.READY : status;
    }

    /**
     * 装配状态枚举
     */
    public enum AssemblyStatus {
        /** 装配成功，所有任务就绪 */
        READY,
        /** 部分装配成功 */
        PARTIAL,
        /** 装配失败 */
        FAILED
    }
}
