package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.model.value.RelayPlan;

/**
 * Outbox 消息发布端口。
 */
public interface OutboxPublisherPort {

    PublishResult publish(OutboxMessage message, RelayPlan plan) throws Exception;

    record PublishResult(String messageId) {
        public static final PublishResult NONE = new PublishResult(null);
    }
}
