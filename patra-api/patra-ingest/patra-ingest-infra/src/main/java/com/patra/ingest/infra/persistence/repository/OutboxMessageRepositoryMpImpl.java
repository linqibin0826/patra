package com.patra.ingest.infra.persistence.repository;

import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.port.OutboxMessageRepository;
import com.patra.ingest.infra.persistence.converter.OutboxMessageConverter;
import com.patra.ingest.infra.persistence.entity.OutboxMessageDO;
import com.patra.ingest.infra.persistence.mapper.OutboxMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 基于 MyBatis-Plus 的 Outbox 仓储实现。
 */
@Repository
@RequiredArgsConstructor
public class OutboxMessageRepositoryMpImpl implements OutboxMessageRepository {

    private final OutboxMessageMapper mapper;
    private final OutboxMessageConverter converter;

    @Override
    public void saveAll(List<OutboxMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        for (OutboxMessage message : messages) {
            OutboxMessageDO entity = converter.toEntity(message);
            mapper.insert(entity);
        }
    }
}
