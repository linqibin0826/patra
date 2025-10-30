package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.model.vo.RelayPlan;

/**
 * Port that publishes outbox messages.
 *
 * <p>Abstracts underlying channels (MQ, webhook, S3, and so on) for reliable message delivery.
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface OutboxPublisherPort {

  /**
   * Publish a single outbox message.
   *
   * @param message outbox entity including payload, headers, and retry counters
   * @param plan relay plan describing retry strategy and lease context
   * @throws Exception to signal publication errors; callers decide whether to retry or fail
   */
  void publish(OutboxMessage message, RelayPlan plan) throws Exception;
}
