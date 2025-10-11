package com.patra.ingest.app.usecase.plan;

import com.patra.ingest.app.usecase.plan.command.PlanIngestionCommand;
import com.patra.ingest.app.usecase.plan.dto.PlanIngestionResult;

/**
 * Application use case for plan orchestration that defines the scheduling entry contract.
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface PlanIngestionUseCase {

    /**
     * Orchestrates and persists plan/slices/tasks and triggers outbox publishing.
     *
     * @param request scheduling request
     * @return summary of orchestration result
     */
    PlanIngestionResult ingestPlan(PlanIngestionCommand request);
}
