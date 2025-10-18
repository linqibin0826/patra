package com.patra.starter.expr.compiler.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;

/**
 * Micrometer-backed metrics recorder for the expression compiler bridge.
 *
 * <p>All methods are no-ops when Micrometer is not on the classpath or no {@link MeterRegistry} is
 * available. Use {@link #noop()} in such cases to avoid null checks throughout the codebase.
 *
 * <p>Metric names (see docs/expr/02-architecture.md §2.6):
 *
 * <ul>
 *   <li>{@code expr.render.rule_hits{provenance,endpoint}}
 *   <li>{@code expr.render.rule_miss{provenance,endpoint}}
 *   <li>{@code expr.param.map_hit{provenance,endpoint}}
 *   <li>{@code expr.param.map_miss{provenance,endpoint}}
 *   <li>{@code expr.transform.applied{provenance,endpoint,code}}
 *   <li>{@code expr.compile.errors{code}}
 *   <li>{@code expr.compile.duration_ms{provenance,endpoint}}
 * </ul>
 *
 * <p>Note: Not final to allow Spring AOP CGLIB proxying.
 *
 * @since 1.0.0
 */
public class ExprMetrics {

  private static final ExprMetrics NO_OP = new ExprMetrics(null);

  private final MeterRegistry meterRegistry;

  /** Protected no-arg constructor for CGLIB proxying. */
  protected ExprMetrics() {
    this(null);
  }

  private ExprMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  /**
   * Creates a Micrometer-backed metrics recorder.
   *
   * @param meterRegistry Micrometer registry (required, non-null)
   * @return metrics recorder
   */
  public static ExprMetrics of(MeterRegistry meterRegistry) {
    Objects.requireNonNull(meterRegistry, "meterRegistry");
    return new ExprMetrics(meterRegistry);
  }

  /**
   * Returns a no-op metrics recorder that skips all instrumentation calls.
   *
   * @return no-op recorder
   */
  public static ExprMetrics noop() {
    return NO_OP;
  }

  public void renderRuleHit(String provenance, String endpoint) {
    if (disabled()) {
      return;
    }
    counter("expr.render.rule_hits", provenance, endpoint).increment();
  }

  public void renderRuleMiss(String provenance, String endpoint) {
    if (disabled()) {
      return;
    }
    counter("expr.render.rule_miss", provenance, endpoint).increment();
  }

  public void paramMapHit(String provenance, String endpoint) {
    if (disabled()) {
      return;
    }
    counter("expr.param.map_hit", provenance, endpoint).increment();
  }

  public void paramMapMiss(String provenance, String endpoint) {
    if (disabled()) {
      return;
    }
    counter("expr.param.map_miss", provenance, endpoint).increment();
  }

  public void transformApplied(String provenance, String endpoint, String transformCode) {
    if (disabled()) {
      return;
    }
    Counter.builder("expr.transform.applied")
        .tag("provenance", safeTag(provenance))
        .tag("endpoint", safeTag(endpoint))
        .tag("code", safeTag(transformCode))
        .register(meterRegistry)
        .increment();
  }

  public void compileError(String code) {
    if (disabled()) {
      return;
    }
    Counter.builder("expr.compile.errors")
        .tag("code", safeTag(code))
        .register(meterRegistry)
        .increment();
  }

  public void compileDuration(String provenance, String endpoint, long durationMillis) {
    if (disabled() || durationMillis < 0) {
      return;
    }
    DistributionSummary.builder("expr.compile.duration_ms")
        .baseUnit("milliseconds")
        .tag("provenance", safeTag(provenance))
        .tag("endpoint", safeTag(endpoint))
        .register(meterRegistry)
        .record(durationMillis);
  }

  private Counter counter(String name, String provenance, String endpoint) {
    return Counter.builder(name)
        .tag("provenance", safeTag(provenance))
        .tag("endpoint", safeTag(endpoint))
        .register(meterRegistry);
  }

  private boolean disabled() {
    return meterRegistry == null;
  }

  private String safeTag(String value) {
    return value == null || value.isBlank() ? "UNKNOWN" : value;
  }
}
