package com.patra.ingest.app.outbox.core;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/// Outbox 发布操作的结果。
///
/// 封装成功/失败计数、错误详情和持续时间指标。
///
/// @author linqibin
/// @since 0.1.0
public final class OutboxPublishResult {

  private final int successCount;
  private final int failureCount;
  private final List<FailureDetail> failures;
  private final Duration duration;

  private OutboxPublishResult(
      int successCount, int failureCount, List<FailureDetail> failures, Duration duration) {
    this.successCount = successCount;
    this.failureCount = failureCount;
    this.failures = List.copyOf(Objects.requireNonNull(failures));
    this.duration = Objects.requireNonNull(duration);
  }

  /// 返回成功发布的消息数量。
  ///
  /// @return 成功计数
  public int getSuccessCount() {
    return successCount;
  }

  /// 返回失败的消息数量。
  ///
  /// @return 失败计数
  public int getFailureCount() {
    return failureCount;
  }

  /// 返回失败详情列表。
  ///
  /// @return 不可修改的失败列表(永远不为 null,可能为空)
  public List<FailureDetail> getFailures() {
    return failures;
  }

  /// 返回操作持续时间。
  ///
  /// @return 持续时间(永远不为 null)
  public Duration getDuration() {
    return duration;
  }

  /// 检查是否有任何失败。
  ///
  /// @return 如果至少有一条消息失败则返回 true,否则返回 false
  public boolean hasFailures() {
    return failureCount > 0;
  }

  /// 创建成功结果。
  ///
  /// @param count 成功发布的消息数量
  /// @param duration 操作持续时间
  /// @return 成功结果
  public static OutboxPublishResult success(int count, Duration duration) {
    return new OutboxPublishResult(count, 0, List.of(), duration);
  }

  /// 创建失败结果。
  ///
  /// @param errorMessage 通用错误消息
  /// @param duration 操作持续时间
  /// @return 失败结果
  public static OutboxPublishResult failure(String errorMessage, Duration duration) {
    FailureDetail detail = new FailureDetail(null, null, errorMessage, "GENERAL_ERROR");
    return new OutboxPublishResult(0, 1, List.of(detail), duration);
  }

  /// 创建部分失败结果(部分成功,部分失败)。
  ///
  /// @param successCount 成功的消息数量
  /// @param failures 失败详情列表
  /// @param duration 操作持续时间
  /// @return 部分失败结果
  public static OutboxPublishResult partial(
      int successCount, List<FailureDetail> failures, Duration duration) {
    return new OutboxPublishResult(successCount, failures.size(), failures, duration);
  }

  /// 创建空结果(没有消息需要处理)。
  ///
  /// @param duration 操作持续时间
  /// @return 空结果
  public static OutboxPublishResult empty(Duration duration) {
    return new OutboxPublishResult(0, 0, List.of(), duration);
  }

  /// 合并两个结果(用于批处理)。
  ///
  /// @param other 要合并的另一个结果
  /// @return 合并后的结果
  public OutboxPublishResult merge(OutboxPublishResult other) {
    int totalSuccess = this.successCount + other.successCount;
    int totalFailure = this.failureCount + other.failureCount;

    List<FailureDetail> mergedFailures = new java.util.ArrayList<>(this.failures);
    mergedFailures.addAll(other.failures);

    Duration totalDuration = this.duration.plus(other.duration);

    return new OutboxPublishResult(totalSuccess, totalFailure, mergedFailures, totalDuration);
  }

  /// 单条消息的详细失败信息。
  ///
  /// @param aggregateId 聚合 ID(可为 null)
  /// @param dedupKey 去重键(可为 null)
  /// @param errorMessage 错误消息
  /// @param errorType 错误类型(例如 "VALIDATION_ERROR"、"DB_ERROR"、"JSON_ERROR")
  public record FailureDetail(
      String aggregateId, String dedupKey, String errorMessage, String errorType) {}

  @Override
  public String toString() {
    return String.format(
        "OutboxPublishResult{success=%d, failure=%d, duration=%dms}",
        successCount, failureCount, duration.toMillis());
  }
}
