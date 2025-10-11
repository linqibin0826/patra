package com.patra.ingest.adapter.inbound.scheduler.job;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PubMed HARVEST scheduled job. Binds the PUBMED provenance with the HARVEST operation
 * and delegates parameter parsing and plan orchestration to the base class.
 * <p>This class does not handle business details; it declares provenance/operation
 * and exposes the XXL execution entrypoint.</p>
 */
@Slf4j
@Component
public class PubmedHarvestJob extends AbstractProvenanceScheduleJob {

    /** Fixed PUBMED provenance. */
    @Override
    protected ProvenanceCode getProvenanceCode() {
        return ProvenanceCode.PUBMED;
    }

    /** Fixed HARVEST operation. */
    @Override
    protected OperationCode getOperationCode() {
        return OperationCode.HARVEST;
    }

    /**
     * XXL-Job entrypoint: fetch scheduler param and delegate to {@link #executeScheduleJob(String)}.
     */
    @XxlJob("pubmedHarvest")
    public void run() {
        // Pass XXL param through to the common scheduling logic
        executeScheduleJob(XxlJobHelper.getJobParam());
    }
}
