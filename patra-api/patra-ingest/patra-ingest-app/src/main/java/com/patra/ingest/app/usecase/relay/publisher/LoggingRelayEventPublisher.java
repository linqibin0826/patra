package com.patra.ingest.app.usecase.relay.publisher;

import com.patra.ingest.domain.event.OutboxRelayDomainEvent;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Logging-based event publisher: emits events at DEBUG level for development and diagnostics.
 *
 * <p>Production deployments can increase the log level or replace this publisher with one that
 * forwards metrics.
 */
@Slf4j
@Component
public class LoggingRelayEventPublisher implements RelayEventPublisher {

  @Override
  public void publish(List<OutboxRelayDomainEvent> events) {
    if (events == null || events.isEmpty()) {
      return;
    }
    // Emit event snapshots only at DEBUG level to avoid production noise
    events.forEach(event -> log.debug("relay-event: {}", event));
  }
}
