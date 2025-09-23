package com.patra.ingest.adapter.in.scheduler.job;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.enums.OperationType;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** PubMed UPDATE 任务。 */
@Slf4j
@Component
public class PubmedUpdateJob extends AbstractProvenanceScheduleJob {
    @Override protected ProvenanceCode getProvenanceCode() { return ProvenanceCode.PUBMED; }
    @Override protected OperationType getOperationType() { return OperationType.UPDATE; }
    @XxlJob("pubmedUpdate")
    public void run() { executeScheduleJob(XxlJobHelper.getJobParam()); }
}
