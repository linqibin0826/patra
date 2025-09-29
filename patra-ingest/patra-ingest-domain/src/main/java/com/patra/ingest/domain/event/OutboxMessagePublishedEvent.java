package com.patra.ingest.domain.event;

import java.time.Instant;

/**
 * 消息发布成功事件。
 */
public record OutboxMessagePublishedEvent(
        Long messageId,
        String channel,
        String partitionKey,
        String brokerMessageId,
        Instant occurredAt
) implements OutboxRelayDomainEvent {
}
