package com.patra.ingest.app.validator;

import com.patra.ingest.domain.model.command.PlanTriggerNorm;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.value.PlannerWindow;

public interface PlannerValidator {

    void validateBeforeAssemble(PlanTriggerNorm triggerNorm,
                                ProvenanceConfigSnapshot snapshot,
                                PlannerWindow window,
                                long currentQueuedTasks);
}

