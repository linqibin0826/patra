package dev.patra.analytics.simulator.live;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import tools.jackson.databind.json.JsonMapper;

/// 全程延迟与到达窗口 `latency.json`（报告用：每秒的文件拼不出全程百分位；速率只在到达窗口里算，
/// 不把停发后的收尾算进去）。
///
/// @param requests 请求数（含重试）
/// @param p50Millis p50 毫秒
/// @param p95Millis p95 毫秒
/// @param p99Millis p99 毫秒
/// @param arrivalStart 开始产生会话的时刻
/// @param arrivalEnd 停止产生会话的时刻
public record LatencySummary(
    long requests,
    long p50Millis,
    long p95Millis,
    long p99Millis,
    Instant arrivalStart,
    Instant arrivalEnd) {

  /// 从直方图汇总。
  ///
  /// @param histogram 全程直方图
  /// @param arrivalStart 开始产生会话的时刻
  /// @param arrivalEnd 停止产生会话的时刻
  /// @return 汇总
  public static LatencySummary of(
      LatencyHistogram histogram, Instant arrivalStart, Instant arrivalEnd) {
    return new LatencySummary(
        histogram.count(),
        histogram.percentile(0.50).toMillis(),
        histogram.percentile(0.95).toMillis(),
        histogram.percentile(0.99).toMillis(),
        arrivalStart,
        arrivalEnd);
  }

  /// 写出。
  ///
  /// @param file 路径
  /// @param jsonMapper JSON
  public void write(Path file, JsonMapper jsonMapper) {
    try {
      Files.writeString(file, jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(this));
    } catch (IOException e) {
      throw new UncheckedIOException("写延迟汇总失败：" + file, e);
    }
  }

  /// 读入。
  ///
  /// @param file 路径
  /// @param jsonMapper JSON
  /// @return 汇总
  public static LatencySummary read(Path file, JsonMapper jsonMapper) {
    try {
      return jsonMapper.readValue(Files.readString(file), LatencySummary.class);
    } catch (IOException e) {
      throw new UncheckedIOException("读延迟汇总失败：" + file, e);
    }
  }
}
