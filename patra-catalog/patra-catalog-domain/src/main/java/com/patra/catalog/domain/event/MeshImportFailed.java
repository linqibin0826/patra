package com.patra.catalog.domain.event;

import com.patra.catalog.domain.model.valueobject.MeshImportId;
import com.patra.common.domain.DomainEvent;
import java.io.Serial;
import java.time.Instant;

/**
 * MeSH 导入任务失败事件。
 *
 * <p>当任务遇到不可恢复错误时发布。
 *
 * <p><b>事件用途</b>：
 *
 * <ul>
 *   <li>记录失败原因和时间（审计）
 *   <li>通知下游系统任务失败（如告警、日志）
 *   <li>触发错误处理流程（如回滚、补偿）
 *   <li>支持失败分析和故障排查（记录失败原因和已处理数）
 * </ul>
 *
 * <p><b>设计说明</b>：
 *
 * <ul>
 *   <li>不可变性：使用 record 确保事件不可变
 *   <li>领域语义：使用过去时命名（Failed）
 *   <li>包含关键信息：失败原因、已处理记录数、失败时间
 * </ul>
 *
 * @param importId 任务 ID（强类型 ID）
 * @param failureReason 失败原因
 * @param processedRecords 已处理记录数
 * @param failedTime 失败时间
 * @author linqibin
 * @since 0.2.0
 */
public record MeshImportFailed(
    MeshImportId importId, String failureReason, Integer processedRecords, Instant failedTime)
    implements DomainEvent {

  @Serial private static final long serialVersionUID = 1L;

  @Override
  public Instant occurredAt() {
    return failedTime;
  }

  @Override
  public String toString() {
    return String.format(
        "MeshImportFailed[importId=%s, failureReason=%s, processedRecords=%d, failedTime=%s]",
        importId, failureReason, processedRecords, failedTime);
  }
}
