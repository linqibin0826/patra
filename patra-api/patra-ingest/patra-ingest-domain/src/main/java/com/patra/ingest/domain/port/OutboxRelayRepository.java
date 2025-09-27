package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.entity.OutboxMessage;

import java.time.Instant;
import java.util.List;

/**
 * Outbox Relay 仓储端口。
 */
public interface OutboxRelayRepository {

    /**
     * 按频道锁定可发布的 Outbox 记录，采用 FOR UPDATE SKIP LOCKED 防止重复消费。
     *
     * @param channel   出站频道
     * @param available 当前可发布时间基准
     * @param limit     最大拉取数量
     * @return 可发布的消息列表
     */
    List<OutboxMessage> lockPending(String channel, Instant available, int limit);

    /**
     * 抢占租约并标记为发布中。
     *
     * @param id             Outbox 主键
     * @param expectedStatus 预期状态
     * @param expectedVersion 乐观锁版本
     * @param leaseOwner     租约持有者
     * @param leaseExpireAt  租约到期时间
     * @return 是否抢占成功
     */
    boolean markPublishing(Long id, String expectedStatus, Long expectedVersion, String leaseOwner, Instant leaseExpireAt);

    /**
     * 发布成功，标记为 PUBLISHED。
     */
    void markPublished(Long id, Long expectedVersion, String msgId);

    /**
     * 发布失败，计算下一次重试。
     */
    void markRetry(Long id,
                   Long expectedVersion,
                   int retryCount,
                   Instant nextRetryAt,
                   String errorCode,
                   String errorMsg);

    /**
     * 达到重试上限，标记为 DEAD。
     */
    void markDead(Long id,
                  Long expectedVersion,
                  int retryCount,
                  String errorCode,
                  String errorMsg);
}
