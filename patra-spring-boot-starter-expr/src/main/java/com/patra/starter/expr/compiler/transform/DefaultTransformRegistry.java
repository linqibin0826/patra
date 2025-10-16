package com.patra.starter.expr.compiler.transform;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Default immutable implementation of {@link TransformRegistry}. Initialized at construction time
 * with a list of transforms, indexed by code for O(1) lookup. Thread-safe through immutability.
 *
 * <p>See: docs/expr/03-compiler-bridge-internals.md §3.7 (Thread Safety & Performance)
 *
 * @since 1.0.0
 */
public class DefaultTransformRegistry implements TransformRegistry {

  private final Map<String, ValueTransform> transforms;

  /**
   * Constructs a registry from a list of transforms. Transforms are indexed by their code for
   * efficient lookup.
   *
   * @param transformList list of value transforms to register
   * @throws IllegalArgumentException if duplicate transform codes are detected
   */
  public DefaultTransformRegistry(List<ValueTransform> transformList) {
    if (transformList == null) {
      throw new IllegalArgumentException("Transform list cannot be null");
    }

    this.transforms =
        transformList.stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    ValueTransform::code,
                    Function.identity(),
                    (t1, t2) -> {
                      throw new IllegalArgumentException(
                          "Duplicate transform code detected: " + t1.code());
                    }));
  }

  @Override
  public Optional<ValueTransform> find(String code) {
    if (code == null || code.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(transforms.get(code));
  }
}
