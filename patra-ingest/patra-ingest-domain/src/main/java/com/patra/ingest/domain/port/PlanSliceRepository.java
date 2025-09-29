package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;

import java.util.List;

/**
 * 计划切片（Plan Slice）仓储端口。
 * <p>负责持久化规划阶段生成的切片结果，便于任务装配与回放窗口时进行精确查询。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface PlanSliceRepository {

    /**
     * 保存或更新单个计划切片。
     *
     * @param slice 计划切片聚合，包含窗口、过滤条件等信息
     * @return 持久化后的计划切片
     */
    PlanSliceAggregate save(PlanSliceAggregate slice);

    /**
     * 批量保存计划切片。
     *
     * @param slices 切片集合
     * @return 持久化后的切片集合
     */
    List<PlanSliceAggregate> saveAll(List<PlanSliceAggregate> slices);

    /**
     * 按计划 ID 查询全部切片。
     *
     * @param planId 计划 ID
     * @return 该计划对应的切片列表
     */
    List<PlanSliceAggregate> findByPlanId(Long planId);
}
