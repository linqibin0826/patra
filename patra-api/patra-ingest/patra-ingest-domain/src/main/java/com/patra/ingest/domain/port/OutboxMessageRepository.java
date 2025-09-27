package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.entity.OutboxMessage;

import java.util.List;
import java.util.Optional;

/**
 * Outbox 消息仓储端口。
 */
public interface OutboxMessageRepository {

    void saveAll(List<OutboxMessage> messages);

    void saveOrUpdate(OutboxMessage message);

    Optional<OutboxMessage> findByChannelAndDedup(String channel, String dedupKey);
}
