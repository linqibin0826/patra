package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.entity.OutboxMessage;

import java.time.Instant;
import java.util.List;

/**
 * Outbox Relay 持久化端口，负责批量提取与状态流转。
 */
public interface OutboxRelayStore {

    /**
     * 按频道拉取待发布的 Outbox 记录，需满足时间窗口且状态为 PENDING/FAILED。
     *
     * @param channel       频道
     * @param availableTime 当前可发布的时间基准
     * @param limit         最大拉取数量
     * @return 待发布消息列表
     */
    List<OutboxMessage> fetchPending(String channel, Instant availableTime, int limit);

    /**
     * 尝试抢占租约，将消息状态置为 PUBLISHING。
     *
     * @param id              Outbox 主键
     * @param expectedVersion 期望版本（允许为 null）
     * @param leaseOwner      租约持有者
     * @param leaseExpireAt   租约到期时间
     * @return 是否抢占成功
     */
    boolean acquireLease(Long id, Long expectedVersion, String leaseOwner, Instant leaseExpireAt);

    /**
     * 发布成功，标记为 PUBLISHED。
     */
    void markPublished(Long id, Long expectedVersion, String messageId);

    /**
     * 发布失败但仍可重试，回退为 PENDING 并设置下一次尝试时间。
     */
    void markDeferred(Long id,
                      Long expectedVersion,
                      int retryCount,
                      Instant nextRetryAt,
                      String errorCode,
                      String errorMessage);

    /**
     * 发布失败且不可重试，直接标记为 DEAD。
     */
    void markFailed(Long id,
                    Long expectedVersion,
                    int retryCount,
                    String errorCode,
                    String errorMessage);
}
