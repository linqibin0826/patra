package com.patra.ingest.app.usecase;

import com.patra.ingest.app.usecase.command.PlanTriggerCommand;
import com.patra.ingest.app.dto.PlanTriggerResult;

public interface PlanTriggerUseCase {

    PlanTriggerResult triggerPlan(PlanTriggerCommand command);
}
