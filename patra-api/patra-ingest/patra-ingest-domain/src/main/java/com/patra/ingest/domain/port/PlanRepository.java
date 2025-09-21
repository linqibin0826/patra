package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.aggregate.Plan;
import java.util.Optional;

public interface PlanRepository {
    Plan save(Plan plan);
    Optional<Plan> findByPlanKey(String planKey);
}
