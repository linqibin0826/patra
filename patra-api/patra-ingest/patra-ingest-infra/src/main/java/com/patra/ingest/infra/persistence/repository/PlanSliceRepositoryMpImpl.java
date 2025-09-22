package com.patra.ingest.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.patra.ingest.domain.model.aggregate.PlanSlice;
import com.patra.ingest.domain.port.PlanSliceRepository;
import com.patra.ingest.infra.persistence.converter.PlanSliceConverter;
import com.patra.ingest.infra.persistence.entity.PlanSliceDO;
import com.patra.ingest.infra.persistence.mapper.PlanSliceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class PlanSliceRepositoryMpImpl implements PlanSliceRepository {

    private final PlanSliceMapper mapper;
    private final PlanSliceConverter converter;

    @Override
    public PlanSlice save(PlanSlice slice) {
        PlanSliceDO dto = converter.toDO(slice);
        if (dto.getId() == null) {
            mapper.insert(dto);
        } else {
            mapper.updateById(dto);
        }
        return converter.toDomain(dto);
    }

    @Override
    public void saveAll(List<PlanSlice> slices) {
        for (PlanSlice slice : slices) {
            save(slice);
        }
    }

    @Override
    public List<PlanSlice> findByPlanId(Long planId) {
        return mapper.selectList(new QueryWrapper<PlanSliceDO>().eq("plan_id", planId))
            .stream().map(converter::toDomain).collect(Collectors.toList());
    }
}
