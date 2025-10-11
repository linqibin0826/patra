package com.patra.ingest.app.usecase.plan.slicer.model;

import com.patra.expr.Expr;
import java.util.Objects;

/**
 * Slice planning result (Application Layer · DTO).
 * <p>
 * Intermediate representation produced by a slicing strategy; not yet persisted. Includes:
 * <ul>
 *   <li>sequence number: maintains generation order and downstream task sequencing (starts from 1; 0/negative not allowed)</li>
 *   <li>signature seed: canonical JSON used to derive an idempotent signature</li>
 *   <li>window spec JSON: window boundary parameters (may differ from the seed; the former is for idempotency, the latter for execution semantics)</li>
 *   <li>expression: compiled expression (Expr) that filters/constrains the slice's data</li>
 * </ul>
 * </p>
 * <h4>Invariants</h4>
 * <ul>
 *   <li>{@code sliceNo >= 1}</li>
 *   <li>{@code sliceSignatureSeed != null && !sliceSignatureSeed.isBlank()}</li>
 *   <li>{@code windowSpecJson != null && !windowSpecJson.isBlank()}</li>
 *   <li>{@code sliceExpr != null}</li>
 * </ul>
 * <h4>Thread-safety</h4>
 * <p>Record is immutable and can be safely shared across threads.</p>
 * <h4>Downstream</h4>
 * <p>Will be converted into a domain-layer Slice aggregate or directly dispatched as task parameters.
 * The signature seed participates in SHA-256 (or similar) to derive an idempotency key.</p>
 *
 * @param sliceNo            slice sequence number (starts from 1)
 * @param sliceSignatureSeed canonical JSON seed used to compute the slice signature
 * @param windowSpecJson     canonical JSON describing the window boundary
 * @param sliceExpr          compiled expression for this slice
 *
 * @author linqibin
 * @since 0.1.0
 */
public record SlicePlan(int sliceNo,
                        String sliceSignatureSeed,
                        String windowSpecJson,
                        Expr sliceExpr) {
    public SlicePlan {
        // Validate critical fields to ensure downstream can access the required information
        Objects.requireNonNull(sliceSignatureSeed, "sliceSignatureSeed must not be null");
        Objects.requireNonNull(windowSpecJson, "windowSpecJson must not be null");
        Objects.requireNonNull(sliceExpr, "sliceExpr must not be null");
    }
}
