package com.patra.ingest.app.usecase.relay.publisher;

import com.patra.ingest.domain.event.OutboxRelayDomainEvent;
import java.util.List;

/// 发布 Outbox 中继领域事件的抽象
/// 
/// 实现可以将事件转发到日志、监控系统、消息队列或本地事件总线
/// 
/// 契约: 容忍单个事件失败并记录日志,避免中断主中继流程
public interface RelayEventPublisher {

  /// 批量发布事件 (批量调用时保留原始顺序)
/// 
/// @param events 事件列表,可能为空或 `null`
  void publish(List<OutboxRelayDomainEvent> events);
}
