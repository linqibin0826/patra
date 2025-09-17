package com.patra.ingest.adapter.in.job;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.app.usecase.StartPlanUseCase;
import com.patra.ingest.adapter.in.job.model.JobParameterDto;
import com.patra.ingest.adapter.in.job.parser.JobParameterParser;
import com.patra.ingest.adapter.in.job.validator.JobParameterValidator;
import com.patra.ingest.adapter.in.job.mapper.JobParameterMapper;
import com.patra.ingest.domain.model.enums.IngestOperationType;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.xxl.job.core.context.XxlJobHelper;
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

    @XxlJob("pubmedHarvestJob")
    public ReturnT<String> pubmedHarvestJob() {
        final IngestOperationType operationType = IngestOperationType.HARVEST;
        log.info("Starting PubMed harvest job with operation type: {}", operationType);
        try {
            // 1) Read raw JSON from XXL-Job
            String rawParam = XxlJobHelper.getJobParam();
            log.info("Received XXL-Job param: {}", rawParam);

            // 2) Parse to DTO
            JobParameterDto dto = JobParameterParser.parse(rawParam);

            // 3) Validate provided (non-null) fields
            JobParameterValidator.validate(dto);

            // 4) Map to app-layer command
            String requestedBy = "xxl-job"; // could be enhanced with executor identity
            var command = JobParameterMapper.toJobStartPlanCommand(
                    dto,
                    requestedBy,
                    provenanceCode,
                    operationType
            );

            // 5) Invoke use case
            Long planId = startPlanUseCase.startPlan(command);
            String okMsg = "PubMed harvest plan started, planId=" + planId;
            XxlJobHelper.log(okMsg);
            log.info(okMsg);
            return new ReturnT<>(ReturnT.SUCCESS_CODE, okMsg);
        } catch (IllegalArgumentException e) {
            String msg = "Invalid job parameters: " + e.getMessage();
            XxlJobHelper.log(msg);
            log.warn(msg, e);
            return new ReturnT<>(ReturnT.FAIL_CODE, msg);
        } catch (Exception e) {
            String msg = "Failed to execute PubMed harvest job: " + e.getMessage();
            XxlJobHelper.log(msg);
            log.error(msg, e);
            return new ReturnT<>(ReturnT.FAIL_CODE, msg);
        }
    }


}
