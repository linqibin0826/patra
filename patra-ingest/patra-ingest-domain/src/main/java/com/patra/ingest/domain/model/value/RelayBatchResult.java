package com.patra.ingest.domain.model.value;

import com.patra.ingest.domain.event.OutboxRelayDomainEvent;

import java.util.Collections;
import java.util.List;

/**
 * Relay 批次执行结果。
 */
public record RelayBatchResult(
        com.patra.ingest.domain.messaging.ChannelKey channel,
        int fetched,
        int published,
        int retried,
        int failed,
        int leaseMissed,
        List<OutboxRelayDomainEvent> events
) {
    public RelayBatchResult {
        events = events == null ? List.of() : List.copyOf(events);
    }

    public static RelayBatchResult empty(com.patra.ingest.domain.messaging.ChannelKey channel) {
        return new RelayBatchResult(channel, 0, 0, 0, 0, 0, Collections.emptyList());
    }
}
