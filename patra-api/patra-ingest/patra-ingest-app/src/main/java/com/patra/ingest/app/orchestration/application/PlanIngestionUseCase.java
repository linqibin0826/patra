package com.patra.ingest.app.orchestration.application;

import com.patra.ingest.app.orchestration.command.PlanIngestionRequest;
import com.patra.ingest.app.orchestration.dto.PlanIngestionResult;

public interface PlanIngestionUseCase {

    PlanIngestionResult ingestPlan(PlanIngestionRequest request);
}
