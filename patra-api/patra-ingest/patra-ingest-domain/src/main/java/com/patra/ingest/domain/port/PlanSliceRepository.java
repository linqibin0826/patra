package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.aggregate.PlanSlice;
import java.util.List;

public interface PlanSliceRepository {
    PlanSlice save(PlanSlice slice);
    void saveAll(List<PlanSlice> slices);
    List<PlanSlice> findByPlanId(Long planId);
}
