package com.patra.catalog.domain.event;

import com.patra.catalog.domain.model.valueobject.MeshImportId;
import com.patra.common.domain.DomainEvent;
import java.io.Serial;
import java.time.Instant;

/// MeSH 导入任务失败事件。
///
/// 当任务遇到不可恢复错误时发布。
///
/// **事件用途**：
///
/// - 记录失败原因和时间（审计）
///   - 通知下游系统任务失败（如告警、日志）
///   - 触发错误处理流程（如回滚、补偿）
///   - 支持失败分析和故障排查（记录失败原因和已处理数）
///
/// **设计说明**：
///
/// - 不可变性：使用 record 确保事件不可变
///   - 领域语义：使用过去时命名（Failed）
///   - 包含关键信息：失败原因、已处理记录数、失败时间
///
/// @param importId 任务 ID（强类型 ID）
/// @param failureReason 失败原因
/// @param processedRecords 已处理记录数
/// @param failedTime 失败时间
/// @author linqibin
/// @since 0.1.0
public record MeshImportFailed(
    MeshImportId importId, String failureReason, Integer processedRecords, Instant failedTime)
    implements DomainEvent {

  @Serial private static final long serialVersionUID = 1L;

  /// {@inheritDoc}
  ///
  /// 返回任务失败时间作为事件发生时刻。
  @Override
  public Instant occurredAt() {
    return failedTime;
  }

  /// 返回事件的字符串表示形式。
  ///
  /// @return 包含所有关键信息的格式化字符串
  @Override
  public String toString() {
    return String.format(
        "MeshImportFailed[importId=%s, failureReason=%s, processedRecords=%d, failedTime=%s]",
        importId, failureReason, processedRecords, failedTime);
  }
}
