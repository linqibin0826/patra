package com.patra.starter.expr.compiler.function;

import java.util.Optional;

/**
 * Registry for render-time functions referenced by {@code fn_code} in render rules. Provides lookup
 * of function implementations by code.
 *
 * <p>Implementations must be thread-safe and immutable after initialization. Lookups are O(1) using
 * internal map structures.
 *
 * <p>See: docs/expr/03-compiler-bridge-internals.md §3.3
 *
 * @since 1.0.0
 */
public interface FunctionRegistry {

  /**
   * Finds a render function by its code identifier.
   *
   * @param code function code (e.g., "PUBMED_DATETYPE")
   * @return Optional containing the function if found, empty otherwise
   */
  Optional<RenderFunction> find(String code);
}
