package com.patra.starter.feign.error.observation;

import com.patra.starter.feign.error.config.FeignErrorProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * 基于 Micrometer 的 Feign 错误观察记录器实现
 *
 * <p>使用 Micrometer 指标系统记录 Feign 错误解码过程中的各类可观测性数据。
 *
 * <h3>记录的指标</h3>
 *
 * <ul>
 *   <li><b>patra.feign.error.parsing</b> - ProblemDetail 解析性能 (Timer)
 *   <li><b>patra.feign.error.decoding</b> - 错误解码结果统计 (Counter)
 *   <li><b>patra.feign.error.body.read</b> - 响应体读取性能 (Timer)
 *   <li><b>patra.feign.error.traceid</b> - 跟踪标识符提取统计 (Counter)
 * </ul>
 *
 * <h3>性能监控</h3>
 *
 * <ul>
 *   <li>当解析耗时超过阈值时记录警告日志
 *   <li>当响应体读取缓慢时记录警告日志
 *   <li>容错模式使用情况可选记录
 * </ul>
 *
 * @see FeignErrorObservationRecorder
 */
@Slf4j
public class MicrometerFeignErrorObservationRecorder implements FeignErrorObservationRecorder {

  private final MeterRegistry meterRegistry;
  private final FeignErrorProperties.ObservationProperties observationProperties;

  public MicrometerFeignErrorObservationRecorder(
      MeterRegistry meterRegistry, FeignErrorProperties properties) {
    this.meterRegistry = meterRegistry;
    this.observationProperties = properties.getObservation();
  }

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
