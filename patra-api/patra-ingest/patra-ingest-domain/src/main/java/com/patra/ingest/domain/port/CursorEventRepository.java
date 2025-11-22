package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.entity.CursorEvent;

/// Cursor 推进事件仓储端口(六边形架构 - Domain → Infrastructure)。
///
/// **职责**: 持久化 Cursor 推进的只追加(append-only)事件记录,捕获 Cursor 演化血缘,用于:
///
/// - 审计跟踪 - 记录每次 Cursor 推进的完整上下文
///   - 状态回放 - 支持历史状态重建
///   - 监控告警 - 分析 Cursor 推进模式
///
/// **端口语义**: 此接口是六边形架构中的 **仓储端口(Repository Port)**,定义在 Domain
/// 层,由基础设施层(Infrastructure)实现,确保领域逻辑与持久化技术解耦。
///
/// @author linqibin
/// @since 0.1.0
public interface CursorEventRepository {

  /// 持久化单个 Cursor 推进事件。
  ///
  /// **业务含义**: 记录一次 Cursor 推进的快照,包括推进窗口、来源批次、触发时间等元数据。
  ///
  /// @param event Cursor 事件实体,包含标识符、时间窗口、血缘信息、元数据
  /// @return 已持久化的事件实体(通常包含自动生成的标识符)
  CursorEvent save(CursorEvent event);
}
