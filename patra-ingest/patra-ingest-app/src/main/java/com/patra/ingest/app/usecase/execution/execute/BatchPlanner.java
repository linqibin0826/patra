package com.patra.ingest.app.usecase.execution.execute;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.vo.BatchPlan;
import com.patra.ingest.domain.model.vo.ExecutionContext;

/**
 * Batch planner interface.
 * <p>
 * Responsibility: plan batches from the execution context; customizable per provenance.
 * </p>
 * <p>
 * Design notes:
 * <ul>
 *   <li>Strategy: different provenanceCodes may use different planning strategies.</li>
 *   <li>Batch limit: enforce max batch count; throw or mark exceedsLimit accordingly.</li>
 *   <li>Cursor support: support cursor-based pagination (e.g., token-based).</li>
 *   <li>Window-aware: adjust query range based on WindowSpec strategy (TIME/ID_RANGE/CURSOR_LANDMARK/etc.).</li>
 * </ul>
 * </p>
 * <p>
 * Implementations should be registered in BatchPlannerRegistry and routed by provenanceCode.
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface BatchPlanner {

    /**
     * Returns the supported provenance code.
     *
     * @return provenance code
     */
    ProvenanceCode getProvenanceCode();

    /**
     * Plans batches.
     *
     * @param context execution context (query/params/window/configSnapshot)
     * @param maxBatches maximum number of batches
     * @return batch plan
     */
    BatchPlan plan(ExecutionContext context, int maxBatches);
}
