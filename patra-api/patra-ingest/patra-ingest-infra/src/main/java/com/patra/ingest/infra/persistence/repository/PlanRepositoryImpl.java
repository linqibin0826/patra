package com.patra.ingest.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.patra.ingest.domain.model.aggregate.Plan;
import com.patra.ingest.domain.port.PlanRepository;
import com.patra.ingest.infra.mapstruct.PlanConverter;
import com.patra.ingest.infra.persistence.entity.PlanDO;
import com.patra.ingest.infra.persistence.mapper.IngPlanMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 计划蓝图仓储实现。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Repository
@RequiredArgsConstructor
public class PlanRepositoryImpl implements PlanRepository {

    private final IngPlanMapper mapper;
    private final PlanConverter converter;

    @Override
    public Optional<Plan> findById(Long id) {
        var obj = mapper.selectById(id);
        return Optional.ofNullable(obj).map(converter::toAggregate);
    }

    @Override
    public Optional<Plan> findByPlanKey(String planKey) {
        var q = new LambdaQueryWrapper<PlanDO>()
                .eq(PlanDO::getPlanKey, planKey)
                .last("limit 1");
        var obj = mapper.selectOne(q);
        return Optional.ofNullable(obj).map(converter::toAggregate);
    }

    @Override
    public List<Plan> findByScheduleInstanceId(Long scheduleInstanceId) {
        var q = new LambdaQueryWrapper<PlanDO>()
                .eq(PlanDO::getScheduleInstanceId, scheduleInstanceId)
                .orderByAsc(PlanDO::getId);
        var list = mapper.selectList(q);
        return list.stream().map(converter::toAggregate).toList();
    }

    @Override
    public Plan save(Plan plan) {
        var toSave = converter.toDO(plan);
        if (toSave.getId() == null) mapper.insert(toSave); else mapper.updateById(toSave);
        return converter.toAggregate(toSave);
    }
}

