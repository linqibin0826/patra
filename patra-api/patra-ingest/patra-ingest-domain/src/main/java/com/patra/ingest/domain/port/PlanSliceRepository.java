package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;

import java.util.List;

public interface PlanSliceRepository {
    PlanSliceAggregate save(PlanSliceAggregate slice);

    List<PlanSliceAggregate> saveAll(List<PlanSliceAggregate> slices);

    List<PlanSliceAggregate> findByPlanId(Long planId);
}
