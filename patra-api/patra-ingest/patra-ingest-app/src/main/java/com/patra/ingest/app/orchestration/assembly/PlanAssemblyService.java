package com.patra.ingest.app.orchestration.assembly;

import com.patra.ingest.domain.model.aggregate.PlanAssembly;

/**
 * Coordinates transformation from trigger request into persisted plan artefacts.
 */
public interface PlanAssemblyService {

    PlanAssembly assemble(PlanAssemblyRequest request);
}
