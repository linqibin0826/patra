package com.patra.ingest.infra.persistence.repository;

import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.model.enums.OutboxStatus;
import com.patra.ingest.domain.port.OutboxMessageRepository;
import com.patra.ingest.domain.port.OutboxRelayRepository;
import com.patra.ingest.infra.persistence.converter.OutboxMessageConverter;
import com.patra.ingest.infra.persistence.entity.OutboxMessageDO;
import com.patra.ingest.infra.persistence.mapper.OutboxMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 基于 MyBatis-Plus 的 Outbox 仓储实现。
 */
@Repository
@RequiredArgsConstructor
public class OutboxMessageRepositoryMpImpl implements OutboxMessageRepository, OutboxRelayRepository {

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

    @Override
    public void saveOrUpdate(OutboxMessage message) {
        if (message == null) {
            return;
        }
        OutboxMessageDO entity = converter.toEntity(message);
        if (entity.getId() == null) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
    }

    @Override
    public Optional<OutboxMessage> findByChannelAndDedup(String channel, String dedupKey) {
        OutboxMessageDO entity = mapper.findByChannelAndDedup(channel, dedupKey);
        return Optional.ofNullable(entity).map(converter::toDomain);
    }

    @Override
    public List<OutboxMessage> lockPending(String channel, Instant available, int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        List<OutboxMessageDO> entities = mapper.lockPending(channel, OutboxStatus.PENDING.name(), available, limit);
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }
        return entities.stream().map(converter::toDomain).toList();
    }

    @Override
    public boolean markPublishing(Long id, String expectedStatus, Long expectedVersion, String leaseOwner, Instant leaseExpireAt) {
        int updated = mapper.markPublishing(id, expectedStatus, expectedVersion, leaseOwner, leaseExpireAt);
        return updated == 1;
    }

    @Override
    public void markPublished(Long id, Long expectedVersion, String msgId) {
        int updated = mapper.markPublished(id, expectedVersion, msgId);
        if (updated != 1) {
            throw new IllegalStateException("更新 Outbox 状态失败，id=" + id);
        }
    }

    @Override
    public void markRetry(Long id, Long expectedVersion, int retryCount, Instant nextRetryAt, String errorCode, String errorMsg) {
        int updated = mapper.markRetry(id, expectedVersion, retryCount, nextRetryAt, errorCode, errorMsg);
        if (updated != 1) {
            throw new IllegalStateException("写回 Outbox 重试失败，id=" + id);
        }
    }

    @Override
    public void markDead(Long id, Long expectedVersion, int retryCount, String errorCode, String errorMsg) {
        int updated = mapper.markDead(id, expectedVersion, retryCount, errorCode, errorMsg);
        if (updated != 1) {
            throw new IllegalStateException("标记 Outbox DEAD 失败，id=" + id);
        }
    }
}
