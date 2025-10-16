package com.patra.starter.expr.compiler.function;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Default immutable implementation of {@link FunctionRegistry}. Initialized at construction time
 * with a list of functions, indexed by code for O(1) lookup. Thread-safe through immutability.
 *
 * <p>See: docs/expr/03-compiler-bridge-internals.md §3.7 (Thread Safety & Performance)
 *
 * @since 1.0.0
 */
public class DefaultFunctionRegistry implements FunctionRegistry {

  private final Map<String, RenderFunction> functions;

  /**
   * Constructs a registry from a list of functions. Functions are indexed by their code for
   * efficient lookup.
   *
   * @param functionList list of render functions to register
   * @throws IllegalArgumentException if duplicate function codes are detected
   */
  public DefaultFunctionRegistry(List<RenderFunction> functionList) {
    if (functionList == null) {
      throw new IllegalArgumentException("Function list cannot be null");
    }

    this.functions =
        functionList.stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    RenderFunction::code,
                    Function.identity(),
                    (f1, f2) -> {
                      throw new IllegalArgumentException(
                          "Duplicate function code detected: " + f1.code());
                    }));
  }

  @Override
  public Optional<RenderFunction> find(String code) {
    if (code == null || code.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(functions.get(code));
  }
}
