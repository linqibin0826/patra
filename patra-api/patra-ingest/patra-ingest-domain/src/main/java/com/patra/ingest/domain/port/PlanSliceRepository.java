package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.entity.PlanSlice;

import java.util.List;
import java.util.Optional;

/**
 * 计划切片仓储端口。
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface PlanSliceRepository {

    Optional<PlanSlice> findById(Long id);

    List<PlanSlice> findByPlanId(Long planId);

    Optional<PlanSlice> findBySliceSignature(String sliceSignatureHash);

    PlanSlice save(PlanSlice slice);

    void saveAll(List<PlanSlice> slices);
}

