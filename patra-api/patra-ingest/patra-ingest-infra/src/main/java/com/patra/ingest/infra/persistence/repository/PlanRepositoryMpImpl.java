package com.patra.ingest.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.patra.ingest.domain.model.aggregate.Plan;
import com.patra.ingest.domain.port.PlanRepository;
import com.patra.ingest.infra.persistence.converter.PlanConverter;
import com.patra.ingest.infra.persistence.entity.PlanDO;
import com.patra.ingest.infra.persistence.mapper.PlanMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PlanRepositoryMpImpl implements PlanRepository {
    private final PlanMapper mapper;
    private final PlanConverter converter;

    @Override
    public Plan save(Plan plan) {
        PlanDO dto = converter.toDO(plan);
        if (dto.getId() == null) mapper.insert(dto); else mapper.updateById(dto);
        return converter.toDomain(dto);
    }

    @Override
    public Optional<Plan> findByPlanKey(String planKey) {
        PlanDO found = mapper.selectOne(new LambdaQueryWrapper<PlanDO>().eq(PlanDO::getPlanKey, planKey));
        return Optional.ofNullable(found).map(converter::toDomain);
    }
}
