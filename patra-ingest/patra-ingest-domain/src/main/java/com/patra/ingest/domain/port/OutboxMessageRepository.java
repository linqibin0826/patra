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

    /**
     * 批量查询指定频道和幂等键的 Outbox 消息。
     * <p>用于补偿发布场景的批量幂等性检查，支持 publishRetry 性能优化。</p>
     *
     * @param channel   频道标识
     * @param dedupKeys 幂等键集合（建议 ≤500 以避免 IN 子句性能问题）
     * @return 匹配的消息列表，若无匹配则返回空列表
     */
    List<OutboxMessage> findByChannelAndDedupIn(String channel, List<String> dedupKeys);

    /**
     * 批量更新 Outbox 消息（用于补偿发布场景的状态刷新）。
     * <p>典型场景：retry 时重置已存在消息的状态为 PENDING、更新 payload/headers、清零重试计数。</p>
     *
     * @param messages 待更新的消息集合（必须包含有效的 ID）
     */
    void updateBatch(List<OutboxMessage> messages);

    /**
     * 批量插入或更新 Outbox 消息（UPSERT 语义）。
     * <p>基于唯一约束（channel + dedupKey）实现幂等性：</p>
     * <ul>
     *   <li>若消息不存在，则插入新记录</li>
     *   <li>若消息已存在（dedupKey 冲突），则更新 payload/headers/status，并重置 retryCount</li>
     * </ul>
     * <p>此方法解决 publishRetry 并发场景的 race condition 问题（两个实例同时 retry 同一消息）。</p>
     *
     * @param messages 待插入或更新的消息集合
     */
    void upsertBatch(List<OutboxMessage> messages);
}
