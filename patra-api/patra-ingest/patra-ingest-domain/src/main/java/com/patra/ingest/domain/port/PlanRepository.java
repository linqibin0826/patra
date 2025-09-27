package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.aggregate.PlanAggregate;

import java.util.Optional;

/**
 * 计划仓储端口定义。
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface PlanRepository {
    /** 保存或更新计划聚合 */
    PlanAggregate save(PlanAggregate plan);

    /** 根据 planKey 查询计划 */
    Optional<PlanAggregate> findByPlanKey(String planKey);

    /** 判断 planKey 是否存在 */
    boolean existsByPlanKey(String planKey);
}
