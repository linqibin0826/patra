package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.model.vo.RelayPlan;

/**
 * Port that publishes outbox messages.
 * <p>Abstracts underlying channels (MQ, webhook, S3, and so on) and returns a unified result so the relay can
 * continue its state transitions.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface OutboxPublisherPort {

    /**
     * Publish a single outbox message.
     *
     * @param message outbox entity including payload, headers, and retry counters
     * @param plan    relay plan describing retry strategy and lease context
     * @return publish result containing downstream message id, or {@link PublishResult#NONE}
     * @throws Exception to signal publication errors; callers decide whether to retry or fail
     */
    PublishResult publish(OutboxMessage message, RelayPlan plan) throws Exception;

    /**
     * Value object representing the publish result.
     *
     * @param messageId downstream message identifier (nullable)
     */
    record PublishResult(String messageId) {
        /** Default result when no message id is returned. */
        public static final PublishResult NONE = new PublishResult(null);
    }
}
