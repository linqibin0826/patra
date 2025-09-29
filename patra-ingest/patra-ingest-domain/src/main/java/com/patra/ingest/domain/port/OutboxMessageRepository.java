package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.entity.OutboxMessage;

import java.util.List;
import java.util.Optional;

/**
 * Outbox 消息仓储端口。
 * <p>负责存储待发布的消息、支持幂等校验与批量写入，常与 {@link OutboxRelayStore} 协同完成消息生命周期管理。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface OutboxMessageRepository {

    /**
     * 批量写入 Outbox 消息。
     *
     * @param messages 待持久化的消息集合
     */
    void saveAll(List<OutboxMessage> messages);

    /**
     * 保存或更新单条 Outbox 消息。
     *
     * @param message 消息实体，包含幂等键、有效载荷与状态
     */
    void saveOrUpdate(OutboxMessage message);

    /**
     * 根据频道及幂等键查询已有消息，常用于防重。
     *
     * @param channel  频道标识
     * @param dedupKey 幂等键
     * @return 若存在则返回对应消息，否则返回 empty
     */
    Optional<OutboxMessage> findByChannelAndDedup(String channel, String dedupKey);
}
