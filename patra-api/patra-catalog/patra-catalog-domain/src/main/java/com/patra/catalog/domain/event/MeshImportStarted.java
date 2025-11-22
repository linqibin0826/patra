package com.patra.catalog.domain.event;

import com.patra.catalog.domain.model.valueobject.MeshImportId;
import com.patra.common.domain.DomainEvent;
import java.io.Serial;
import java.time.Instant;

/// MeSH 导入任务启动事件。
/// 
/// 当任务从 PENDING 转换为 PROCESSING 状态时发布。
/// 
/// **事件用途**：
/// 
/// - 记录任务启动时间（审计）
///   - 通知下游系统任务开始（如日志、监控）
///   - 触发相关业务流程（如资源预留）
/// 
/// **设计说明**：
/// 
/// - 不可变性：使用 record 确保事件不可变
///   - 领域语义：使用过去时命名（Started）
///   - 包含关键上下文：任务 ID、数据源 URL、开始时间
/// 
/// @param importId 任务 ID（强类型 ID）
/// @param sourceUrl 数据源 URL
/// @param startTime 开始时间
/// @author linqibin
/// @since 0.2.0
public record MeshImportStarted(MeshImportId importId, String sourceUrl, Instant startTime)
    implements DomainEvent {

  @Serial private static final long serialVersionUID = 1L;

  @Override
  public Instant occurredAt() {
    return startTime;
  }

  @Override
  public String toString() {
    return String.format(
        "MeshImportStarted[importId=%s, sourceUrl=%s, startTime=%s]",
        importId, sourceUrl, startTime);
  }
}
