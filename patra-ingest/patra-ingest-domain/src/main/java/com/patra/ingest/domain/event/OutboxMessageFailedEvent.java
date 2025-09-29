package com.patra.ingest.domain.event;

import java.time.Instant;

/**
 * 消息被标记为 DEAD 的事件。
 */
public record OutboxMessageFailedEvent(
        Long messageId,
        String channel,
        int retryCount,
        String errorCode,
        String errorMessage,
        Instant occurredAt
) implements OutboxRelayDomainEvent {
}
