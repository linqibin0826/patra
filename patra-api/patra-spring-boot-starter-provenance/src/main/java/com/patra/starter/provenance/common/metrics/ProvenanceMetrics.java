package com.patra.starter.provenance.common.metrics;

import dev.linqibin.patra.common.enums.ProvenanceCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Objects;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/// Provenance 度量记录器
///
/// 使用 Micrometer 计时器和计数器包装 provenance API 调用,使下游服务能够监控每个数据源的延迟和错误率。
///
/// **记录的指标:**
///
/// - provenance.client.api.duration - API 调用持续时间(按数据源和API名称分组)
///   - provenance.client.api.success - 成功调用计数
///   - provenance.client.api.failure - 失败调用计数(按错误类型分类)
///   - provenance.conversion.success - 转换成功计数
///   - provenance.conversion.failure - 转换失败计数
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public class ProvenanceMetrics {

  private final MeterRegistry meterRegistry;

  /// 创建 Provenance 度量记录器。
  ///
  /// @param meterRegistry Micrometer 指标注册表
  public ProvenanceMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry cannot be null");
  }

  /// 记录 provenance API 调用的成功/失败度量
  ///
  /// @param provenanceCode provenance 数据源标识符
  /// @param apiName API 名称(esearch, efetch, search 等)
  /// @param supplier API 调用逻辑
  /// @param <T> 结果类型
  /// @return supplier 的结果
  public <T> T recordApiCall(ProvenanceCode provenanceCode, String apiName, Supplier<T> supplier) {
    Objects.requireNonNull(provenanceCode, "provenanceCode cannot be null");
    Objects.requireNonNull(apiName, "apiName cannot be null");
    Objects.requireNonNull(supplier, "supplier cannot be null");

    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      T result = supplier.get();
      recordTimer(provenanceCode, apiName, "success", sample);
      incrementCounter("provenance.client.api.success", provenanceCode, apiName, null);
      return result;
    } catch (RuntimeException ex) {
      recordTimer(provenanceCode, apiName, "failure", sample);
      incrementCounter(
          "provenance.client.api.failure", provenanceCode, apiName, ex.getClass().getSimpleName());
      log.debug(
          "API call failed: provenance={} api={} error={}",
          provenanceCode.getCode(),
          apiName,
          ex.getClass().getSimpleName());
      throw ex;
    }
  }

  /// 记录计时器度量。
  ///
  /// @param code 数据源代码
  /// @param apiName API 名称
  /// @param status 状态（success 或 failure）
  /// @param sample 计时器采样
  private void recordTimer(
      ProvenanceCode code, String apiName, String status, Timer.Sample sample) {
    sample.stop(
        Timer.builder("provenance.client.api.duration")
            .tag("provenanceCode", code.getCode())
            .tag("apiName", apiName)
            .tag("status", status)
            .register(meterRegistry));
  }

  /// 增加计数器（带错误类型）。
  ///
  /// @param metricName 指标名称
  /// @param code 数据源代码
  /// @param apiName API 名称
  /// @param errorType 错误类型（可为 null）
  private void incrementCounter(
      String metricName, ProvenanceCode code, String apiName, String errorType) {
    incrementCounter(metricName, code, apiName, errorType, 1.0d);
  }

  /// 增加计数器（带错误类型和自定义数量）。
  ///
  /// @param metricName 指标名称
  /// @param code 数据源代码
  /// @param apiName API 名称
  /// @param errorType 错误类型（可为 null）
  /// @param amount 增加量
  private void incrementCounter(
      String metricName, ProvenanceCode code, String apiName, String errorType, double amount) {
    Counter.Builder builder =
        Counter.builder(metricName).tag("provenanceCode", code.getCode()).tag("apiName", apiName);
    if (errorType != null) {
      builder.tag("errorType", errorType);
    }
    builder.register(meterRegistry).increment(amount);
  }

  /// 增加计数器（仅数据源代码）。
  ///
  /// @param metricName 指标名称
  /// @param code 数据源代码
  /// @param amount 增加量
  private void incrementCounter(String metricName, ProvenanceCode code, double amount) {
    Counter.builder(metricName)
        .tag("provenanceCode", code.getCode())
        .register(meterRegistry)
        .increment(amount);
  }

  /// 记录标准化出版物转换的度量
  ///
  /// @param code provenance 数据源
  /// @param successCount 成功转换的记录数
  /// @param failureCount 失败转换的记录数
  public void recordConversionMetrics(ProvenanceCode code, int successCount, int failureCount) {
    if (successCount > 0) {
      incrementCounter("provenance.conversion.success", code, successCount);
    }
    if (failureCount > 0) {
      incrementCounter("provenance.conversion.failure", code, failureCount);
    }
  }
}
