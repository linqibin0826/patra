package com.patra.ingest.domain.event;

import java.time.Instant;

/**
 * 消息重试计划事件。
 */
public record OutboxMessageDeferredEvent(
        Long messageId,
        String channel,
        int nextRetryCount,
        Instant nextRetryAt,
        String errorCode,
        String errorMessage,
        Instant occurredAt
) implements OutboxRelayDomainEvent {
}
