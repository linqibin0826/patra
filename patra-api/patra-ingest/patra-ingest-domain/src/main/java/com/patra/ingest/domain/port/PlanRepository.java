package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.aggregate.PlanAggregate;

import java.util.Optional;

/**
 * 计划聚合仓储端口。
 * <p>负责采集计划的持久化、查重与查询，保障计划生成与回放时的一致性。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface PlanRepository {

    /**
     * 保存或更新单个计划聚合。
     *
     * @param plan 计划聚合，包含计划窗口、触发规范与切片策略
     * @return 持久化后的计划聚合
     */
    PlanAggregate save(PlanAggregate plan);

    /**
     * 通过 planKey 查询计划聚合。
     *
     * @param planKey 计划唯一键（来源 + 操作 + 窗口等计算而得）
     * @return 若存在返回对应计划，否则返回 empty
     */
    Optional<PlanAggregate> findByPlanKey(String planKey);

    /**
     * 判断指定 planKey 是否已存在。
     *
     * @param planKey 计划唯一键
     * @return 存在返回 {@code true}，否则 {@code false}
     */
    boolean existsByPlanKey(String planKey);
}
