package com.patra.ingest.domain.event;

import dev.linqibin.commons.domain.DomainEvent;
import java.time.Instant;

/// 任务完成领域事件。当任务执行完成时发布（无论成功或失败）。
///
/// 触发时机：任务转换到终态（SUCCEEDED、FAILED、PARTIAL）后触发。
///
/// 用途：
///
/// - 聚合：触发基于所有子任务重新计算父切片状态
///   - 指标：按来源和操作度量任务完成率、成功率和失败率
///   - 审计：追踪任务从创建到完成的生命周期
///   - 监控：针对高失败率或卡住的任务发出告警
///
/// 幂等性：`taskId` 作为唯一键。处理器必须检查状态是否已被处理。
///
/// 事件链：TaskCompletedEvent → SliceStatusChangedEvent → PlanAggregate 状态更新。
public record TaskCompletedEvent(
    /* Primary identifier of the completed task. */
    Long taskId,
    /* Identifier of the owning slice. */
    Long sliceId,
    /* Identifier of the owning plan. */
    Long planId,
    /* Final status of the task (SUCCEEDED, FAILED, CURSOR_PENDING, etc). */
    String status,
    /* Error code if task failed (null if succeeded). */
    String errorCode,
    /* Error message if task failed (null if succeeded). */
    String errorMessage,
    /* Timestamp when the task finished execution. */
    Instant finishedAt,
    /* Timestamp when the event occurred. */
    Instant occurredAt)
    implements DomainEvent {

  public TaskCompletedEvent {
    // Ensure the event timestamp is always populated.
    occurredAt = occurredAt == null ? Instant.now() : occurredAt;
  }

  /// Factory method for successful task completion.
  ///
  /// @param taskId task identifier
  /// @param sliceId slice identifier
  /// @param planId plan identifier
  /// @param status task status
  /// @param finishedAt completion timestamp
  /// @return event instance
  public static TaskCompletedEvent of(
      Long taskId, Long sliceId, Long planId, String status, Instant finishedAt) {
    return new TaskCompletedEvent(
        taskId, sliceId, planId, status, null, null, finishedAt, Instant.now());
  }

  /// Factory method for failed task completion with error details.
  ///
  /// @param taskId task identifier
  /// @param sliceId slice identifier
  /// @param planId plan identifier
  /// @param status task status
  /// @param errorCode error code
  /// @param errorMessage error message
  /// @param finishedAt completion timestamp
  /// @return event instance
  public static TaskCompletedEvent ofFailure(
      Long taskId,
      Long sliceId,
      Long planId,
      String status,
      String errorCode,
      String errorMessage,
      Instant finishedAt) {
    return new TaskCompletedEvent(
        taskId, sliceId, planId, status, errorCode, errorMessage, finishedAt, Instant.now());
  }
}
