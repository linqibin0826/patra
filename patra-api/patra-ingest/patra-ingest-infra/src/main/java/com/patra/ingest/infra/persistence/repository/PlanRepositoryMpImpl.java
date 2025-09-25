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

@Slf4j
@Repository
@RequiredArgsConstructor
public class PlanRepositoryMpImpl implements PlanRepository {

    private final PlanMapper planMapper;
    private final PlanConverter planConverter;

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

    @Override
    public Optional<PlanAggregate> findByPlanKey(String planKey) {
        if (planKey == null || planKey.isBlank()) {
            return Optional.empty();
        }
        PlanDO entity = planMapper.findByPlanKey(planKey);
        return Optional.ofNullable(entity).map(planConverter::toAggregate);
    }

    @Override
    public boolean existsByPlanKey(String planKey) {
        if (planKey == null || planKey.isBlank()) {
            return false;
        }
        return planMapper.countByPlanKey(planKey) > 0;
    }
}
