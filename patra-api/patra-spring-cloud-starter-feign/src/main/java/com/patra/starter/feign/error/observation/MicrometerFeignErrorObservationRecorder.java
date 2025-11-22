package com.patra.starter.feign.error.observation;

import com.patra.starter.feign.error.config.FeignErrorProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/// 基于 Micrometer 的 Feign 错误观察记录器实现
///
/// 使用 Micrometer 指标系统记录 Feign 错误解码过程中的各类可观测性数据。
///
/// ### 记录的指标
///
/// - **patra.feign.error.parsing** - ProblemDetail 解析性能 (Timer)
///   - **patra.feign.error.decoding** - 错误解码结果统计 (Counter)
///   - **patra.feign.error.body.read** - 响应体读取性能 (Timer)
///   - **patra.feign.error.traceid** - 跟踪标识符提取统计 (Counter)
///
/// ### 性能监控
///
/// - 当解析耗时超过阈值时记录警告日志
///   - 当响应体读取缓慢时记录警告日志
///   - 容错模式使用情况可选记录
///
/// @see FeignErrorObservationRecorder
@Slf4j
public class MicrometerFeignErrorObservationRecorder implements FeignErrorObservationRecorder {

  private final MeterRegistry meterRegistry;
  private final FeignErrorProperties.ObservationProperties observationProperties;

  /// 构造基于 Micrometer 的观察记录器。
  ///
  /// @param meterRegistry Micrometer 指标注册表
  /// @param properties Feign 错误配置属性
  public MicrometerFeignErrorObservationRecorder(
      MeterRegistry meterRegistry, FeignErrorProperties properties) {
    this.meterRegistry = meterRegistry;
    this.observationProperties = properties.getObservation();
  }

  /// 记录 ProblemDetail 解析的性能和结果。
  ///
  /// 使用 Timer 指标记录解析耗时,当超过慢解析阈值时记录警告日志。
  ///
  /// @param methodKey Feign 方法标识
  /// @param status HTTP 状态码
  /// @param durationMs 解析耗时(毫秒)
  /// @param success 是否解析成功
  @Override
  public void recordProblemDetailParsing(
      String methodKey, int status, long durationMs, boolean success) {
    Timer.builder("patra.feign.error.parsing")
        .tag("method", methodKey)
        .tag("status", String.valueOf(status))
        .tag("success", Boolean.toString(success))
        .register(meterRegistry)
        .record(durationMs, TimeUnit.MILLISECONDS);

    if (observationProperties.isLogSlowParsing()
        && durationMs >= observationProperties.getSlowParsingThresholdMs()) {
      log.warn(
          "Feign ProblemDetail 解析缓慢: method={} status={} duration={}ms",
          methodKey,
          status,
          durationMs);
    }

    if (!success) {
      log.debug(
          "Feign ProblemDetail 解析失败: method={} status={} duration={}ms",
          methodKey,
          status,
          durationMs);
    }
  }

  /// 记录错误解码的结果和容错模式使用情况。
  ///
  /// 使用 Counter 指标统计解码成功率和容错模式触发次数。
  ///
  /// @param methodKey Feign 方法标识
  /// @param status HTTP 状态码
  /// @param success 解码是否成功
  /// @param tolerantMode 是否使用了容错模式
  @Override
  public void recordDecodingOutcome(
      String methodKey, int status, boolean success, boolean tolerantMode) {
    Counter.builder("patra.feign.error.decoding")
        .tag("method", methodKey)
        .tag("status", String.valueOf(status))
        .tag("success", Boolean.toString(success))
        .tag("tolerant", Boolean.toString(tolerantMode))
        .register(meterRegistry)
        .increment();

    if (tolerantMode && observationProperties.isLogTolerantUsage()) {
      log.info("Feign 错误解码调用了容错模式: method={} status={}", methodKey, status);
    }
  }

  /// 记录响应体读取的性能和截断情况。
  ///
  /// 使用 Timer 指标记录读取耗时,当超过慢读取阈值时记录警告日志。
  ///
  /// @param methodKey Feign 方法标识
  /// @param bodySize 响应体大小(字节)
  /// @param durationMs 读取耗时(毫秒)
  /// @param truncated 是否被截断
  @Override
  public void recordResponseBodyRead(
      String methodKey, int bodySize, long durationMs, boolean truncated) {
    Timer.builder("patra.feign.error.body.read")
        .tag("method", methodKey)
        .tag("truncated", Boolean.toString(truncated))
        .register(meterRegistry)
        .record(durationMs, TimeUnit.MILLISECONDS);

    if (observationProperties.isLogSlowBodyReading()
        && durationMs >= observationProperties.getSlowBodyReadingThresholdMs()) {
      log.warn(
          "Feign 响应体读取缓慢: method={} size={} duration={}ms truncated={}",
          methodKey,
          bodySize,
          durationMs,
          truncated);
    }
  }

  /// 记录跟踪标识符提取的结果。
  ///
  /// 使用 Counter 指标统计跟踪标识符的提取成功率和使用的响应头类型。
  ///
  /// @param methodKey Feign 方法标识
  /// @param found 是否找到跟踪标识符
  /// @param headerName 跟踪标识符所在的响应头名称,未找到时为 null
  @Override
  public void recordTraceIdExtraction(String methodKey, boolean found, String headerName) {
    Counter.builder("patra.feign.error.traceid")
        .tag("method", methodKey)
        .tag("found", Boolean.toString(found))
        .tag("header", headerName == null ? "none" : headerName)
        .register(meterRegistry)
        .increment();

    if (found) {
      log.debug("Feign 响应头包含 TraceId: method={} header={}", methodKey, headerName);
    } else {
      log.debug("Feign 响应头不包含 TraceId: method={}", methodKey);
    }
  }
}
