package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.entity.PlanSlice;
import java.util.List;

public interface PlanSliceRepository {
    void saveAll(List<PlanSlice> slices);
    List<PlanSlice> findByPlanId(Long planId);
}
