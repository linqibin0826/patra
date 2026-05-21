package dev.linqibin.patra.starter.expr.compiler.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;

/// 基于 Micrometer 的表达式编译器网桥的指标记录器。
///
/// 当 Micrometer 不在 classpath 中或没有可用的 {@link MeterRegistry} 时，所有方法都是空操作。 在这种情况下，使用 {@link #noop()}
/// 以避免在整个代码库中进行 null 检查。
///
/// 指标名称（见 docs/expr/02-architecture.md §2.6）：
///
/// - `expr.render.rule_hits{provenance,endpoint`}
///   - `expr.render.rule_miss{provenance,endpoint`}
///   - `expr.param.map_hit{provenance,endpoint`}
///   - `expr.param.map_miss{provenance,endpoint`}
///   - `expr.transform.applied{provenance,endpoint,code`}
///   - `expr.compile.errors{code`}
///   - `expr.compile.duration_ms{provenance,endpoint`}
///
/// 注意：非 final，以允许 Spring AOP CGLIB 代理。
///
/// @since 0.1.0
public class ExprMetrics {

  private static final ExprMetrics NO_OP = new ExprMetrics(null);

  private final MeterRegistry meterRegistry;

  /// CGLIB 代理的受保护的无参构造函数。
  protected ExprMetrics() {
    this(null);
  }

  /// 私有构造函数。
  ///
  /// @param meterRegistry Micrometer 注册表（可选，null 时创建空操作实例）
  private ExprMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  /// 创建一个基于 Micrometer 的指标记录器。
  ///
  /// @param meterRegistry Micrometer 注册表（必需，非空）
  /// @return 指标记录器
  public static ExprMetrics of(MeterRegistry meterRegistry) {
    Objects.requireNonNull(meterRegistry, "meterRegistry");
    return new ExprMetrics(meterRegistry);
  }

  /// 返回一个跳过所有仪器调用的空操作指标记录器。
  ///
  /// @return 空操作记录器
  public static ExprMetrics noop() {
    return NO_OP;
  }

  /// 记录渲染规则命中。
  ///
  /// @param provenance 数据源代码
  /// @param endpoint 端点名称
  public void renderRuleHit(String provenance, String endpoint) {
    if (disabled()) {
      return;
    }
    counter("expr.render.rule_hits", provenance, endpoint).increment();
  }

  /// 记录渲染规则未命中。
  ///
  /// @param provenance 数据源代码
  /// @param endpoint 端点名称
  public void renderRuleMiss(String provenance, String endpoint) {
    if (disabled()) {
      return;
    }
    counter("expr.render.rule_miss", provenance, endpoint).increment();
  }

  /// 记录参数映射命中。
  ///
  /// @param provenance 数据源代码
  /// @param endpoint 端点名称
  public void paramMapHit(String provenance, String endpoint) {
    if (disabled()) {
      return;
    }
    counter("expr.param.map_hit", provenance, endpoint).increment();
  }

  /// 记录参数映射未命中。
  ///
  /// @param provenance 数据源代码
  /// @param endpoint 端点名称
  public void paramMapMiss(String provenance, String endpoint) {
    if (disabled()) {
      return;
    }
    counter("expr.param.map_miss", provenance, endpoint).increment();
  }

  /// 记录变换已应用。
  ///
  /// @param provenance 数据源代码
  /// @param endpoint 端点名称
  /// @param transformCode 变换代码
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

  /// 记录编译错误。
  ///
  /// @param code 错误代码
  public void compileError(String code) {
    if (disabled()) {
      return;
    }
    Counter.builder("expr.compile.errors")
        .tag("code", safeTag(code))
        .register(meterRegistry)
        .increment();
  }

  /// 记录编译耗时。
  ///
  /// @param provenance 数据源代码
  /// @param endpoint 端点名称
  /// @param durationMillis 耗时（毫秒）
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

  /// 创建计数器。
  ///
  /// @param name 指标名称
  /// @param provenance 数据源代码
  /// @param endpoint 端点名称
  /// @return 计数器实例
  private Counter counter(String name, String provenance, String endpoint) {
    return Counter.builder(name)
        .tag("provenance", safeTag(provenance))
        .tag("endpoint", safeTag(endpoint))
        .register(meterRegistry);
  }

  /// 检查指标记录是否被禁用。
  ///
  /// @return 如果未配置注册表则返回 true
  private boolean disabled() {
    return meterRegistry == null;
  }

  /// 安全处理标签值。
  ///
  /// @param value 标签值
  /// @return 处理后的标签值（null 或空白时返回 "UNKNOWN"）
  private String safeTag(String value) {
    return value == null || value.isBlank() ? "UNKNOWN" : value;
  }
}
