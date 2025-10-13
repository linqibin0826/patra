package com.patra.ingest.app.usecase.relay.publisher;

import com.patra.ingest.domain.event.OutboxRelayDomainEvent;
import java.util.List;

/**
 * Abstraction for publishing Outbox Relay domain events.
 *
 * <p>Implementations may forward events to logs, monitoring systems, message queues, or local event
 * buses.
 *
 * <p>Contract: tolerate individual event failures and log them to avoid disrupting the main relay
 * flow.
 */
public interface RelayEventPublisher {

  /**
   * Publish a batch of events (preserve original order when invoked in bulk).
   *
   * @param events list of events, possibly empty or {@code null}
   */
  void publish(List<OutboxRelayDomainEvent> events);
}
