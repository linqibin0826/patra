package com.patra.ingest.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.port.PlanSliceRepository;
import com.patra.ingest.infra.persistence.converter.PlanSliceConverter;
import com.patra.ingest.infra.persistence.entity.PlanSliceDO;
import com.patra.ingest.infra.persistence.mapper.PlanSliceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class PlanSliceRepositoryMpImpl implements PlanSliceRepository {

    private final PlanSliceMapper mapper;
    private final PlanSliceConverter converter;

    @Override
    public PlanSliceAggregate save(PlanSliceAggregate slice) {
        PlanSliceDO entity = converter.toEntity(slice);
        if (entity.getId() == null) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
        return converter.toAggregate(entity);
    }

    @Override
    public List<PlanSliceAggregate> saveAll(List<PlanSliceAggregate> slices) {
        List<PlanSliceAggregate> persisted = new ArrayList<>(slices.size());
        for (PlanSliceAggregate slice : slices) {
            persisted.add(save(slice));
        }
        return persisted;
    }

    @Override
    public List<PlanSliceAggregate> findByPlanId(Long planId) {
        return mapper.selectList(new QueryWrapper<PlanSliceDO>().eq("plan_id", planId))
                .stream()
                .map(converter::toAggregate)
                .collect(Collectors.toList());
    }
}
