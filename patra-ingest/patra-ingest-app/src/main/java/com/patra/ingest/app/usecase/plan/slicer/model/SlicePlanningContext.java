package com.patra.ingest.app.usecase.plan.slicer.model;

import com.patra.ingest.app.usecase.plan.expression.PlanExpressionDescriptor;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.PlanTriggerNorm;
import com.patra.ingest.domain.model.vo.PlannerWindow;
import java.util.Objects;

/**
 * Slicing strategy execution context (Application-layer input model).
 *
 * <p>Constructed centrally during plan assembly to ensure that when entering {@code SlicePlanner}:
 *
 * <ul>
 *   <li>the trigger norm (mode/step/operation) is normalized;
 *   <li>the planning window is finalized (UTC half-open semantics [from, to));
 *   <li>the business expression is compiled (with JSON snapshot and hash for idempotency and
 *       debugging);
 *   <li>the provenance snapshot (if any) matches the expression logic.
 * </ul>
 *
 * <h4>Semantics</h4>
 *
 * <ul>
 *   <li><b>norm</b>: non-null; encapsulates trigger mode (e.g., HARVEST/BACKFILL/UPDATE), slicing
 *       step, and precedence.
 *   <li><b>window</b>: non-null; half-open interval; when window.to() is null it indicates an open
 *       upper bound.
 *   <li><b>planExpression</b>: non-null; its hash is used in slice signatures/idempotency later.
 *   <li><b>configSnapshot</b>: nullable; some tests or temporary scenarios may omit provenance
 *       config (strategy should handle nulls gracefully).
 * </ul>
 *
 * <h4>Invariants</h4>
 *
 * <ul>
 *   <li>{@code norm != null}
 *   <li>{@code window != null}
 *   <li>{@code planExpression != null}
 * </ul>
 *
 * <h4>Thread-safety</h4>
 *
 * <p>Record is immutable; does not expose mutable collections; safe across threads.
 *
 * <h4>Extension</h4>
 *
 * <p>If lease/ratelimiting information is needed in the future, add new fields while preserving
 * record compatibility (or migrate to a class).
 *
 * @param norm trigger norm including mode, step, etc. (required)
 * @param window planning window using UTC half-open semantics (required)
 * @param planExpression compiled expression descriptor with Expr, snapshot JSON, and hash
 *     (required)
 * @param configSnapshot provenance/source configuration snapshot; optional (strategy should
 *     fallback to defaults when null)
 * @author linqibin
 * @since 0.1.0
 */
public record SlicePlanningContext(
    PlanTriggerNorm norm,
    PlannerWindow window,
    PlanExpressionDescriptor planExpression,
    ProvenanceConfigSnapshot configSnapshot) {
  public SlicePlanningContext {
    Objects.requireNonNull(norm, "norm must not be null");
    Objects.requireNonNull(window, "window must not be null");
    Objects.requireNonNull(planExpression, "planExpression must not be null");
    // configSnapshot may be null (some tests or invocations might not provide it)
  }
}
