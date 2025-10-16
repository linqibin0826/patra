package com.patra.starter.expr.compiler.transform;

import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;

/**
 * Parameter-level transform applied after std_key → provider parameter mapping. Transforms operate
 * on final mapped values (provider-specific semantics).
 *
 * <p>Execution occurs during the compiler phase, after all std_keys have been mapped to provider
 * parameter names. Transforms are identified by their code and applied via {@code transform_code}
 * in param map entries.
 *
 * <p>Example: {@code TO_EXCLUSIVE_MINUS_1D} subtracts one day from a date value to convert
 * exclusive upper bounds to inclusive provider bounds.
 *
 * <p>See: docs/expr/03-compiler-bridge-internals.md §3.3.1
 *
 * @since 1.0.0
 */
public interface ValueTransform {

  /**
   * Returns the unique transform code identifier. This code is referenced in param map {@code
   * transform_code} fields.
   *
   * @return transform code (e.g., "TO_EXCLUSIVE_MINUS_1D")
   */
  String code();

  /**
   * Applies the transform logic to a single mapped std_key value. Returns the transformed value
   * suitable for the provider parameter.
   *
   * <p>The snapshot provides access to provenance configuration if needed for context-aware
   * transforms.
   *
   * @param stdKey the std_key being transformed (e.g., "to", "query", "filter")
   * @param value the mapped value before transformation (e.g., "2023-12-31", "cancer AND therapy")
   * @param snapshot provenance snapshot for context-aware transform logic
   * @return transformed value (e.g., "2023-12-30" after subtracting one day)
   */
  String apply(String stdKey, String value, ProvenanceSnapshot snapshot);
}
