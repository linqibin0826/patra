package com.patra.ingest.infra.persistence.repository;

import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.port.PlanRepository;
import com.patra.ingest.infra.persistence.converter.PlanConverter;
import com.patra.ingest.infra.persistence.entity.PlanDO;
import com.patra.ingest.infra.persistence.mapper.PlanMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 基于 MyBatis-Plus 的计划仓储实现。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class PlanRepositoryMpImpl implements PlanRepository {

    /** 计划 Mapper */
    private final PlanMapper planMapper;
    /** 聚合与 DO 转换器 */
    private final PlanConverter planConverter;

    /**
     * 按是否存在 ID 执行插入或更新。
     */
    @Override
    public PlanAggregate save(PlanAggregate plan) {
        PlanDO entity = planConverter.toEntity(plan);
        if (entity.getId() == null) {
            planMapper.insert(entity);
        } else {
            planMapper.updateById(entity);
        }
        return planConverter.toAggregate(entity);
    }

    /**
     * 根据 planKey 查询计划。
     */
    @Override
    public Optional<PlanAggregate> findByPlanKey(String planKey) {
        if (planKey == null || planKey.isBlank()) {
            return Optional.empty();
        }
        PlanDO entity = planMapper.findByPlanKey(planKey);
        return Optional.ofNullable(entity).map(planConverter::toAggregate);
    }

    /**
     * 判断 planKey 是否存在。
     */
    @Override
    public boolean existsByPlanKey(String planKey) {
        if (planKey == null || planKey.isBlank()) {
            return false;
        }
        return planMapper.countByPlanKey(planKey) > 0;
    }
}
