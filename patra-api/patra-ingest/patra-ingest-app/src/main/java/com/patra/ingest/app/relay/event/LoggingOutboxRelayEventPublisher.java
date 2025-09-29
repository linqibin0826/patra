package com.patra.ingest.app.relay.event;

import com.patra.ingest.domain.event.OutboxRelayDomainEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 默认事件发布器：记录领域事件日志，便于观测与调试。
 */
@Slf4j
@Component
public class LoggingOutboxRelayEventPublisher implements OutboxRelayEventPublisher {

    @Override
    public void publish(List<OutboxRelayDomainEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        events.forEach(event -> log.debug("relay-event: {}", event));
    }
}
