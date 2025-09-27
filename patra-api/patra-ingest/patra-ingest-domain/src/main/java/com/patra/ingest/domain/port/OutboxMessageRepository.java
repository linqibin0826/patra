package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.entity.OutboxMessage;

import java.util.List;

/**
 * Outbox 消息仓储端口。
 */
public interface OutboxMessageRepository {

    void saveAll(List<OutboxMessage> messages);
}
