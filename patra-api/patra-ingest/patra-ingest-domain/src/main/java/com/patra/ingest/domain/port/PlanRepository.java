package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.aggregate.PlanAggregate;

import java.util.Optional;

public interface PlanRepository {
    PlanAggregate save(PlanAggregate plan);

    Optional<PlanAggregate> findByPlanKey(String planKey);

    boolean existsByPlanKey(String planKey);
}
