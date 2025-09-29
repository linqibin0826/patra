package com.patra.ingest.app.outbox.event;

import com.patra.ingest.domain.event.OutboxRelayDomainEvent;

import java.util.List;

/**
 * Outbox Relay 领域事件发布器。
 */
public interface OutboxRelayEventPublisher {

    void publish(List<OutboxRelayDomainEvent> events);
}
