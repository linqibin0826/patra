package com.patra.starter.core.error.observation;

import com.patra.starter.core.error.config.ErrorProperties;
import com.patra.starter.core.error.model.ErrorResolution;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;

/// 基于 Micrometer 的错误观测记录器实现。
/// 
/// 使用 Micrometer 指标库发布错误解析的时间序列指标,支持与 Prometheus、Grafana 等监控系统集成。
/// 
/// 记录的指标:
/// 
/// - `patra.error.resolution.duration` - 解析耗时(Timer)
///   - `patra.error.resolution.count` - 解析次数(Counter)
///   - `patra.error.resolution.slow` - 慢解析次数(Counter)
///   - `patra.error.resolution.circuit_breaker` - 熔断降级次数(Counter)
/// 
/// 所有指标包含标签: `context`(上下文前缀)、`exception`(异常类型)、`errorCode`(错误码)
/// 
/// @author Patra Team
/// @since 2.0
public class MicrometerErrorObservationRecorder implements ErrorObservationRecorder {

  private final MeterRegistry meterRegistry;
  private final String contextPrefix;

  /// 构造 Micrometer 观测记录器。
/// 
/// @param meterRegistry Micrometer 指标注册表
/// @param errorProperties 错误配置属性(提供上下文前缀用于指标标签)
  public MicrometerErrorObservationRecorder(
      MeterRegistry meterRegistry, ErrorProperties errorProperties) {
    this.meterRegistry = meterRegistry;
    String prefix = errorProperties.getContextPrefix();
    this.contextPrefix = (prefix == null || prefix.isBlank()) ? "UNKNOWN" : prefix;
  }

  /// 记录错误解析的性能指标。
/// 
/// 记录内容:
/// 
/// - 解析耗时(Timer): patra.error.resolution.duration
///   - 解析计数(Counter): patra.error.resolution.count
///   - 慢解析计数(Counter): patra.error.resolution.slow(仅当 slow=true)
/// 
/// @param exception 待解析的原始异常
/// @param resolution 解析结果
/// @param durationMs 解析耗时(毫秒)
/// @param slow 是否为慢解析
  @Override
  @SuppressWarnings("resource")
  public void recordResolution(
      Throwable exception, ErrorResolution resolution, long durationMs, boolean slow) {
    String exceptionName = exception == null ? "空异常" : exception.getClass().getSimpleName();
    Timer.builder("patra.error.resolution.duration")
        .tag("context", contextPrefix)
        .tag("exception", exceptionName)
        .tag("errorCode", resolution.errorCode().code())
        .register(meterRegistry)
        .record(durationMs, TimeUnit.MILLISECONDS);

    Counter.builder("patra.error.resolution.count")
        .tag("context", contextPrefix)
        .tag("errorCode", resolution.errorCode().code())
        .register(meterRegistry)
        .increment();

    if (slow) {
      Counter.builder("patra.error.resolution.slow")
          .tag("context", contextPrefix)
          .tag("exception", exceptionName)
          .register(meterRegistry)
          .increment();
    }
  }

  /// 记录熔断器降级指标。
/// 
/// 当熔断器打开时,增加 `patra.error.resolution.circuit_breaker` 计数器。
/// 
/// @param exception 触发降级的原始异常
  @Override
  public void recordCircuitBreakerFallback(Throwable exception) {
    String exceptionName = exception == null ? "空异常" : exception.getClass().getSimpleName();
    Counter.builder("patra.error.resolution.circuit_breaker")
        .tag("context", contextPrefix)
        .tag("exception", exceptionName)
        .register(meterRegistry)
        .increment();
  }
}
