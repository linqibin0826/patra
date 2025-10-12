package com.patra.ingest.app.usecase.execution.execute;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.vo.Batch;
import com.patra.ingest.domain.model.vo.BatchPlan;
import com.patra.ingest.domain.model.vo.ExecutionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PubMed batch planner (initial baseline).
 *
 * Responsibility: produce batch plan from the execution context for PubMed.
 *
 * Notes:
 * - This initial implementation returns a single batch using the compiled query and parameters.
 *   No pagination logic is applied yet.
 * - Follow-up implementation will consider retstart/retmax paging and WindowSpec mapping
 *   (e.g., Time → mindate/maxdate) based on ProvenanceConfigSnapshot.PaginationConfig.
 */
@Component
@Slf4j
public class PubmedBatchPlanner implements BatchPlanner {

    @Override
    public ProvenanceCode getProvenanceCode() {
        return ProvenanceCode.PUBMED;
    }

    @Override
    public BatchPlan plan(ExecutionContext context) {
        // TODO: implement pagination-aware planning (retstart/retmax) and window/date parameter mapping
        String query = context.compiledQuery();
        JsonNode params = context.compiledParams();

        log.info("[INGEST][APP] plan batches (stub) provenance={} taskId={} runId={}",
                context.provenanceCode(), context.taskId(), context.runId());

        // Stub: single batch plan using existing query/params
        return BatchPlan.single(Batch.first(query, params));
    }
}
