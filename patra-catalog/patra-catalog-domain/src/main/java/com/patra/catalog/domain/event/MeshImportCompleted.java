package com.patra.catalog.domain.event;

import com.patra.catalog.domain.model.valueobject.MeshImportId;
import com.patra.common.domain.DomainEvent;
import java.io.Serial;
import java.time.Instant;

/// MeSH 导入任务完成事件。
/// 
/// 当所有表导入成功完成时发布。
/// 
/// **事件用途**：
/// 
/// - 记录任务完成时间和耗时（审计）
///   - 通知下游系统任务成功（如索引更新、缓存刷新）
///   - 触发后续业务流程（如数据校验、报告生成）
///   - 性能监控和统计（记录处理总数和耗时）
/// 
/// **设计说明**：
/// 
/// - 不可变性：使用 record 确保事件不可变
///   - 领域语义：使用过去时命名（Completed）
///   - 包含关键指标：总记录数、耗时秒数、完成时间
/// 
/// @param importId 任务 ID（强类型 ID）
/// @param totalRecords 总记录数
/// @param elapsedSeconds 耗时（秒）
/// @param completedTime 完成时间
/// @author linqibin
/// @since 0.2.0
public record MeshImportCompleted(
    MeshImportId importId, Integer totalRecords, Long elapsedSeconds, Instant completedTime)
    implements DomainEvent {

  @Serial private static final long serialVersionUID = 1L;

  @Override
  public Instant occurredAt() {
    return completedTime;
  }

  @Override
  public String toString() {
    return String.format(
        "MeshImportCompleted[importId=%s, totalRecords=%d, elapsedSeconds=%d, completedTime=%s]",
        importId, totalRecords, elapsedSeconds, completedTime);
  }
}
