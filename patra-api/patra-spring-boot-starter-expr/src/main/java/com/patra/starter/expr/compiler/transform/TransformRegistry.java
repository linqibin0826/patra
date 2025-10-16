package com.patra.starter.expr.compiler.transform;

import java.util.Optional;

/**
 * Registry for parameter-level transforms referenced by {@code transform_code} in param map
 * entries. Provides lookup of transform implementations by code.
 *
 * <p>Implementations must be thread-safe and immutable after initialization. Lookups are O(1) using
 * internal map structures.
 *
 * <p>See: docs/expr/03-compiler-bridge-internals.md §3.3
 *
 * @since 1.0.0
 */
public interface TransformRegistry {

  /**
   * Finds a value transform by its code identifier.
   *
   * @param code transform code (e.g., "TO_EXCLUSIVE_MINUS_1D", "LIST_JOIN")
   * @return Optional containing the transform if found, empty otherwise
   */
  Optional<ValueTransform> find(String code);
}
