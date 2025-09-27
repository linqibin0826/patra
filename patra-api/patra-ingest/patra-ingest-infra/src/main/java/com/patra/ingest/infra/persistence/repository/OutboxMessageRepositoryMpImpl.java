package com.patra.ingest.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
import java.util.List;

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
    public List<OutboxMessage> lockPending(String channel, Instant available, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        LambdaQueryWrapper<OutboxMessageDO> wrapper = Wrappers.lambdaQuery(OutboxMessageDO.class)
                .eq(OutboxMessageDO::getChannel, channel)
                .eq(OutboxMessageDO::getStatusCode, OutboxStatus.PENDING.name())
                .apply("(not_before IS NULL OR not_before <= {0})", available)
                .apply("(next_retry_at IS NULL OR next_retry_at <= {0})", available)
                .orderByAsc(OutboxMessageDO::getId)
                .last("LIMIT " + limit + " FOR UPDATE SKIP LOCKED");
        List<OutboxMessageDO> entities = mapper.selectList(wrapper);
        return entities.stream().map(converter::toDomain).toList();
    }

    @Override
    public boolean markPublishing(Long id, String expectedStatus, Long expectedVersion, String leaseOwner, Instant leaseExpireAt) {
        LambdaUpdateWrapper<OutboxMessageDO> update = Wrappers.lambdaUpdate(OutboxMessageDO.class)
                .set(OutboxMessageDO::getStatusCode, OutboxStatus.PUBLISHING.name())
                .set(OutboxMessageDO::getPubLeaseOwner, leaseOwner)
                .set(OutboxMessageDO::getPubLeasedUntil, leaseExpireAt)
                .setSql("version = version + 1")
                .eq(OutboxMessageDO::getId, id)
                .eq(OutboxMessageDO::getStatusCode, expectedStatus)
                .eq(OutboxMessageDO::getVersion, expectedVersion);
        return mapper.update(null, update) == 1;
    }

    @Override
    public void markPublished(Long id, Long expectedVersion, String msgId) {
        LambdaUpdateWrapper<OutboxMessageDO> update = Wrappers.lambdaUpdate(OutboxMessageDO.class)
                .set(OutboxMessageDO::getStatusCode, OutboxStatus.PUBLISHED.name())
                .set(OutboxMessageDO::getMsgId, msgId)
                .set(OutboxMessageDO::getPubLeaseOwner, null)
                .set(OutboxMessageDO::getPubLeasedUntil, null)
                .set(OutboxMessageDO::getErrorCode, null)
                .set(OutboxMessageDO::getErrorMsg, null)
                .set(OutboxMessageDO::getNextRetryAt, null)
                .setSql("retry_count = retry_count, version = version + 1")
                .eq(OutboxMessageDO::getId, id)
                .eq(OutboxMessageDO::getVersion, expectedVersion);
        int updated = mapper.update(null, update);
        if (updated != 1) {
            throw new IllegalStateException("更新 Outbox 状态失败，id=" + id);
        }
    }

    @Override
    public void markRetry(Long id, Long expectedVersion, int retryCount, Instant nextRetryAt, String errorCode, String errorMsg) {
        LambdaUpdateWrapper<OutboxMessageDO> update = Wrappers.lambdaUpdate(OutboxMessageDO.class)
                .set(OutboxMessageDO::getStatusCode, OutboxStatus.PENDING.name())
                .set(OutboxMessageDO::getRetryCount, retryCount)
                .set(OutboxMessageDO::getNextRetryAt, nextRetryAt)
                .set(OutboxMessageDO::getErrorCode, errorCode)
                .set(OutboxMessageDO::getErrorMsg, errorMsg)
                .set(OutboxMessageDO::getMsgId, null)
                .set(OutboxMessageDO::getPubLeaseOwner, null)
                .set(OutboxMessageDO::getPubLeasedUntil, null)
                .setSql("version = version + 1")
                .eq(OutboxMessageDO::getId, id)
                .eq(OutboxMessageDO::getVersion, expectedVersion);
        int updated = mapper.update(null, update);
        if (updated != 1) {
            throw new IllegalStateException("写回 Outbox 重试失败，id=" + id);
        }
    }

    @Override
    public void markDead(Long id, Long expectedVersion, int retryCount, String errorCode, String errorMsg) {
        LambdaUpdateWrapper<OutboxMessageDO> update = Wrappers.lambdaUpdate(OutboxMessageDO.class)
                .set(OutboxMessageDO::getStatusCode, OutboxStatus.DEAD.name())
                .set(OutboxMessageDO::getRetryCount, retryCount)
                .set(OutboxMessageDO::getErrorCode, errorCode)
                .set(OutboxMessageDO::getErrorMsg, errorMsg)
                .set(OutboxMessageDO::getNextRetryAt, null)
                .set(OutboxMessageDO::getMsgId, null)
                .set(OutboxMessageDO::getPubLeaseOwner, null)
                .set(OutboxMessageDO::getPubLeasedUntil, null)
                .setSql("version = version + 1")
                .eq(OutboxMessageDO::getId, id)
                .eq(OutboxMessageDO::getVersion, expectedVersion);
        int updated = mapper.update(null, update);
        if (updated != 1) {
            throw new IllegalStateException("标记 Outbox DEAD 失败，id=" + id);
        }
    }
}
