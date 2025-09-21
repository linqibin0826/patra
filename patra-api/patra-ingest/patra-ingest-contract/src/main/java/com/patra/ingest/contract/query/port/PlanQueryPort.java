package com.patra.ingest.contract.query.port;

import com.patra.ingest.contract.query.view.PlanQuery;
import java.util.Optional;

public interface PlanQueryPort {
    Optional<PlanQuery> findByPlanKey(String planKey);
}
