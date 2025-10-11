package com.patra.ingest.app.usecase.plan.expression;

import com.patra.expr.Expr;
import java.util.Objects;

/**
 * Plan expression descriptor (expression triad: structure + canonical snapshot + hash).
 * <p>
 * Produced by the expression builder during plan assembly to ensure:
 * <ul>
 *   <li>{@code expr}: compiled expression tree ready for evaluation at runtime</li>
 *   <li>{@code jsonSnapshot}: canonicalized JSON derived from the source DSL/params (fallback to "{}" on null/parse errors)</li>
 *   <li>{@code hash}: stable digest (e.g., SHA-256) computed from {@code jsonSnapshot} for idempotency/version/change detection</li>
 * </ul>
 * </p>
 * <h4>Invariants</h4>
 * <ul>
 *   <li>{@code expr != null}</li>
 *   <li>{@code jsonSnapshot != null} (constructor falls back to empty object)</li>
 *   <li>{@code hash != null && !hash.isBlank()}</li>
 * </ul>
 * <h4>Notes</h4>
 * <ul>
 *   <li>We do not enforce direct equality between hash and expr (expr may contain runtime-optimized structures);
 *       the hash is bound only to jsonSnapshot.</li>
 *   <li>Canonicalization guarantees hash stability against superficial DSL changes (field order/whitespace),
 *       avoiding redundant plans.</li>
 *   <li>For debugging, consider logging the hash with a controlled, redacted subset of jsonSnapshot.</li>
 * </ul>
 * <h4>Thread-safety</h4>
 * <p>Record is immutable and can be safely reused.</p>
 *
 * @param expr         compiled business expression (non-null)
 * @param jsonSnapshot canonical JSON snapshot of the expression (defaults to "{}" when null)
 * @param hash         hash signature of the snapshot (non-blank)
 *
 * @author linqibin
 * @since 0.1.0
 */
public record PlanExpressionDescriptor(Expr expr, String jsonSnapshot, String hash) {
    public PlanExpressionDescriptor {
        Objects.requireNonNull(expr, "expr must not be null");
        jsonSnapshot = jsonSnapshot == null ? "{}" : jsonSnapshot;
        Objects.requireNonNull(hash, "hash must not be null");
    }
}
