package com.patra.ingest.domain.event;

import java.time.Instant;

/**
 * 租约抢占失败事件。
 */
public record OutboxLeaseMissedEvent(
        Long messageId,
        String channel,
        String requestedLeaseOwner,
        String currentLeaseOwner,
        Instant occurredAt
) implements OutboxRelayDomainEvent {
}
