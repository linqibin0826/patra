package com.patra.ingest.app.usecase.plan.window;

import com.patra.ingest.domain.model.vo.PlanTriggerNorm;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.PlannerWindow;

import java.time.Instant;

/**
 * Planning window resolution strategy (policy interface).
 * <p>
 * Input: trigger norm + provenance/source config snapshot + previous cursor watermark + current time.
 * Output: the execution window in UTC as a half-open interval [from, to).
 * This interface abstracts the window calculation so different business modes
 * (HARVEST/BACKFILL/UPDATE, etc.) and per-source configurations can be supported.
 * </p>
 * <h4>Key concerns for implementors</h4>
 * <ul>
 *   <li>Operation modes: HARVEST (incremental), BACKFILL (historical), UPDATE (reconcile/fix).</li>
 *   <li>User-provided window precedence: manual window overrides others, then cursor-guided, then defaults.</li>
 *   <li>Cursor watermark: determines lookback behavior and whether gaps (empty windows) may occur.</li>
 *   <li>Safety lag (watermarkLagSeconds): cap by now to avoid near-real-time inconsistency.</li>
 *   <li>Calendar alignment (calendar_align_to): if alignment results in from == to, treat as empty window.</li>
 *   <li>Window length limits: optionally enforce max span or a minimum effective length.</li>
 * </ul>
 * <h4>Return semantics</h4>
 * <ul>
 *   <li>Non-null: a valid half-open window.</li>
 *   <li>null: full-scan (no window bounds). Implementors should return null only when the
 *   semantics are clearly full-scan.</li>
 * </ul>
 * <h4>Complexity</h4>
 * <p>Typical implementations should keep O(1) time complexity and avoid external IO.</p>
 * <h4>Thread-safety</h4>
 * <p>Implementations should be stateless or ensure their internal state is thread-safe so a single
 * instance can be reused.</p>
 * <h4>Extension ideas</h4>
 * <p>Possible extensions include multi-window segmentation, dynamic policy selection, and
 * fallback chains.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface PlanningWindowResolver {

    /**
     * Resolve the execution window.
     * <p>Implementations may branch by operation mode. If multiple cursor concepts are
     * required (e.g., watermark vs. forward cursor), prefer extending the trigger norm
     * or snapshot to pass the needed context.</p>
     *
     * @param triggerNorm     trigger norm (operation type, optional manual window input, mode enum, etc.)
     * @param snapshot        provenance/source configuration snapshot (nullable; implementor should fallback if absent)
     * @param cursorWatermark current cursor watermark (end of the last successful processing; null means first run)
     * @param currentTime     current time injected by caller to aid testing and determinism
     * @return a valid window; null indicates a full-scan or that no window constraint is needed
     */
    PlannerWindow resolveWindow(PlanTriggerNorm triggerNorm,
                                ProvenanceConfigSnapshot snapshot,
                                Instant cursorWatermark,
                                Instant currentTime);
}
