package com.patra.ingest.domain.event;

import dev.linqibin.commons.domain.DomainEvent;
import dev.linqibin.patra.common.enums.ProvenanceCode;
import java.time.Instant;

/// 任务入队领域事件。当任务进入执行队列时发布。
///
/// 触发时机：任务成功创建并持久化到可调度状态后触发。
///
/// 用途：
///
/// - 指标：按来源和操作维度度量任务创建速率
///   - 审计：追溯调度实例和切片到具体任务的关联
///   - 下游：更新实时监控仪表板
///
/// 幂等性：`taskId` 作为唯一键。重复发布表明上游存在问题。
public record TaskQueuedEvent(
    /* Primary identifier of the task. */
    Long taskId,
    /* Identifier of the owning plan. */
    Long planId,
    /* Identifier of the owning slice. */
    Long sliceId,
    /* Scheduling instance identifier. */
    Long scheduleInstanceId,
    /* Provenance code. */
    ProvenanceCode provenanceCode,
    /* Operation code. */
    String operationCode,
    /* Idempotency key for deduplication. */
    String idempotentKey,
    /* Task parameters serialized as JSON. */
    String paramsJson,
    /* Scheduling priority (higher numbers typically mean higher priority). */
    Integer priority,
    /* Planned execution timestamp. */
    Instant scheduledAt,
    /* Timestamp when the event occurred. */
    Instant occurredAt)
    implements DomainEvent {

  public TaskQueuedEvent {
    // Ensure the event timestamp is always populated.
    occurredAt = occurredAt == null ? Instant.now() : occurredAt;
  }

  /// Factory method that creates the event with mandatory context and auto-populates `occurredAt`.
  ///
  /// @param taskId task identifier
  /// @param planId plan identifier
  /// @param sliceId slice identifier
  /// @param scheduleInstanceId scheduling instance identifier
  /// @param provenanceCode provenance code
  /// @param operationCode operation code
  /// @param idempotentKey idempotency key
  /// @param paramsJson task parameters JSON
  /// @param priority scheduling priority
  /// @param scheduledAt planned execution timestamp
  /// @return event instance
  public static TaskQueuedEvent of(
      Long taskId,
      Long planId,
      Long sliceId,
      Long scheduleInstanceId,
      ProvenanceCode provenanceCode,
      String operationCode,
      String idempotentKey,
      String paramsJson,
      Integer priority,
      Instant scheduledAt) {
    return new TaskQueuedEvent(
        taskId,
        planId,
        sliceId,
        scheduleInstanceId,
        provenanceCode,
        operationCode,
        idempotentKey,
        paramsJson,
        priority,
        scheduledAt,
        Instant.now());
  }
}
