package com.patra.ingest.adapter.in.job;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.adapter.in.job.mapper.StartPlanCommandMapper;
import com.patra.ingest.app.usecase.StartPlanUseCase;
import com.patra.ingest.app.usecase.command.JobStartPlanCommand;
import com.patra.ingest.domain.model.enums.IngestOperationType;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class IngestPubmedJobHandler {

    private final ProvenanceCode provenanceCode = ProvenanceCode.PUBMED;

    /**
     * Application layer use case for starting harvesting plans.
     * Handles the core business logic of plan creation and execution.
     */
    private final StartPlanUseCase startPlanUseCase;



    @XxlJob("pubmedDailyHarvestJob")
    public ReturnT<String> pubmedDailyHarvestJob() {
        final IngestOperationType ingestOperationType = IngestOperationType.HARVEST;
        try {
            // Step 1: Parse job parameters from XXL-Job context (optional JSON payload)
            String rawJobParameter = XxlJobHelper.getJobParam();
            // convert job parameters to application layer command
            JobStartPlanCommand jobStartPlanCommand = StartPlanCommandMapper.fromIngestJobJson(
                    rawJobParameter, "todo", provenanceCode, ingestOperationType
            );


            // Step 6: Execute the harvesting plan through application layer use case
            // The adapter delegates all business logic to the application layer
            Long planId = startPlanUseCase.startPlan(jobStartPlanCommand);

            // Step 7: Log successful execution

            return ReturnT.SUCCESS;

        } catch (Throwable throwable) {
            // Log error with full context for troubleshooting
            log.error("[pubmedDailyHarvestJob] Job execution failed with error: {}",
                    throwable.getMessage(), throwable);

            // Provide error information to XXL-Job console for operator visibility
            XxlJobHelper.log("Job execution failed: " + throwable.getMessage());

            return ReturnT.FAIL;
        }
    }


}
