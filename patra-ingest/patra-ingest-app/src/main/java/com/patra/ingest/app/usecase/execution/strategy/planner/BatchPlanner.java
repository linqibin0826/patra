package com.patra.ingest.app.usecase.execution.strategy.planner;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.vo.batch.BatchPlan;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;

/**
 * Batch planner interface.
 *
 * <p>Responsibility: plan batches from the execution context; customizable per provenance.
 *
 * <p>Design notes:
 *
 * <ul>
 *   <li>Strategy: different provenanceCodes may use different planning strategies.
 *   <li>Batch limit: enforce max batch count; throw or mark exceedsLimit accordingly.
 *   <li>Cursor support: support cursor-based pagination (e.g., token-based).
 *   <li>Window-aware: adjust query range based on WindowSpec strategy
 *       (TIME/DATE/ID_RANGE/CURSOR_LANDMARK/etc.).
 * </ul>
 *
 * <p>Implementations should be registered in BatchPlannerRegistry and routed by provenanceCode.
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
   * @return batch plan
   */
  BatchPlan plan(ExecutionContext context);
}
