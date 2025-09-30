package com.patra.ingest.app.usecase.relay.publisher;

import com.patra.ingest.domain.event.OutboxRelayDomainEvent;

import java.util.List;

/**
 * Outbox Relay 领域事件发布器抽象。
 * <p>实现可将事件输出到：日志 / 监控指标 / MQ / 本地事件总线等。</p>
 * <p>约定：实现需容忍单个事件处理失败并记录，避免影响主流程返回。</p>
 */
public interface RelayEventPublisher {

    /**
     * 发布一批事件（批量调用时保持原顺序）。
     * @param events 事件列表（可能为空或 null）
     */
    void publish(List<OutboxRelayDomainEvent> events);
}
