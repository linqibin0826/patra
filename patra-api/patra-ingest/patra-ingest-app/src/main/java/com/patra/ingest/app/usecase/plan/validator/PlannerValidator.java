package com.patra.ingest.app.usecase.plan.validator;

import com.patra.ingest.domain.model.vo.PlanTriggerNorm;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.PlannerWindow;
import com.patra.ingest.domain.exception.PlanValidationException;

/**
 * Contract for pre-flight plan validation.
 *
 * <p>Implementations analyse inputs and environment before plan/slice/task assembly,
 * preventing downstream execution of invalid workloads.</p>
 *
 * <p>Typical validation dimensions (implementations may extend):</p>
 * <ul>
 *   <li>Window sanity (presence, chronological order, duration bounds)</li>
 *   <li>Queue backpressure (queued task thresholds)</li>
 *   <li>Provenance capability alignment (incremental vs. full, offset configuration)</li>
 *   <li>Configuration snapshot completeness</li>
 * </ul>
 *
 * <p>Violations should throw {@link PlanValidationException}. Callers are expected to surface
 * business-level warnings instead of system errors.</p>
 */
public interface PlannerValidator {

    /**
     * Execute validation prior to plan assembly.
     *
     * @param triggerNorm         normalised trigger (provenance/operation/requested window)
     * @param snapshot            provenance configuration snapshot (may be {@code null})
     * @param window              normalised plan window (may be {@code null} for UPDATE operations)
     * @param currentQueuedTasks  current queued task count used for backpressure checks
     * @throws PlanValidationException when validation fails
     */
    void validateBeforeAssemble(PlanTriggerNorm triggerNorm,
                                ProvenanceConfigSnapshot snapshot,
                                PlannerWindow window,
                                long currentQueuedTasks);
}
