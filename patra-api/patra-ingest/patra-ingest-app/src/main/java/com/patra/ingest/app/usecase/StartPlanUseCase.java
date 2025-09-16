package com.patra.ingest.app.usecase;

import com.patra.ingest.app.usecase.command.JobStartPlanCommand;


public interface StartPlanUseCase {

    Long startPlan(JobStartPlanCommand command);

}
