package com.patra.starter.expr.compiler.function;

import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;
import java.util.Map;

/**
 * Render-time function that derives or mutates placeholder values for PARAMS rendering. Functions
 * operate in the std_key/placeholder space (provider-agnostic).
 *
 * <p>Execution occurs during the renderer phase, before template expansion. Functions are
 * identified by their code and applied via {@code fn_code} in render rules.
 *
 * <p>Example: {@code PUBMED_DATETYPE} returns "pdat" or "edat" based on context.
 *
 * <p>See: docs/expr/03-compiler-bridge-internals.md §3.3.1
 *
 * @since 1.0.0
 */
public interface RenderFunction {

  /**
   * Returns the unique function code identifier. This code is referenced in render rule {@code
   * fn_code} fields.
   *
   * @return function code (e.g., "PUBMED_DATETYPE")
   */
  String code();

  /**
   * Applies the function logic to derive or mutate placeholder values. The function may read from
   * or modify the placeholders map.
   *
   * <p>The snapshot provides access to provenance configuration if needed for context-aware logic.
   *
   * @param placeholders mutable map of placeholder names to values (e.g., {"from": "2023-01-01",
   *     "to": "2023-12-31"})
   * @param snapshot provenance snapshot for context-aware function logic
   * @return derived value or modified placeholder value (typically a single placeholder value)
   */
  String apply(Map<String, String> placeholders, ProvenanceSnapshot snapshot);
}
