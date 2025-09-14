package com.patra.ingest.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.patra.ingest.domain.model.entity.PlanSlice;
import com.patra.ingest.domain.port.PlanSliceRepository;
import com.patra.ingest.infra.mapstruct.PlanSliceConverter;
import com.patra.ingest.infra.persistence.entity.PlanSliceDO;
import com.patra.ingest.infra.persistence.mapper.IngPlanSliceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 计划切片仓储实现。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Repository
@RequiredArgsConstructor
public class PlanSliceRepositoryImpl implements PlanSliceRepository {

    private final IngPlanSliceMapper mapper;
    private final PlanSliceConverter converter;

    @Override
    public Optional<PlanSlice> findById(Long id) {
        var obj = mapper.selectById(id);
        return Optional.ofNullable(obj).map(converter::toEntity);
    }

    @Override
    public List<PlanSlice> findByPlanId(Long planId) {
        var q = new LambdaQueryWrapper<PlanSliceDO>()
                .eq(PlanSliceDO::getPlanId, planId)
                .orderByAsc(PlanSliceDO::getSliceNo);
        var list = mapper.selectList(q);
        return list.stream().map(converter::toEntity).toList();
    }

    @Override
    public Optional<PlanSlice> findBySliceSignature(String sliceSignatureHash) {
        var q = new LambdaQueryWrapper<PlanSliceDO>()
                .eq(PlanSliceDO::getSliceSignatureHash, sliceSignatureHash)
                .last("limit 1");
        var obj = mapper.selectOne(q);
        return Optional.ofNullable(obj).map(converter::toEntity);
    }

    @Override
    public PlanSlice save(PlanSlice slice) {
        var toSave = converter.toDO(slice);
        if (toSave.getId() == null) mapper.insert(toSave); else mapper.updateById(toSave);
        return converter.toEntity(toSave);
    }

    @Override
    public void saveAll(List<PlanSlice> slices) {
        if (slices == null || slices.isEmpty()) return;
        for (PlanSlice s : slices) {
            save(s);
        }
    }
}

