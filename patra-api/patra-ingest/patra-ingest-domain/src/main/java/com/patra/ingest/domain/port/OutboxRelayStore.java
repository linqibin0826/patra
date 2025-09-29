package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.entity.OutboxMessage;

import java.time.Instant;
import java.util.List;

/**
 * Outbox Relay 持久化端口，负责批量提取待发布消息并驱动状态流转。
 * <p>该端口屏蔽底层数据存储细节，支撑发布流程完成租约抢占、发布成功/失败反馈以及重试窗口调度。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface OutboxRelayStore {

    /**
     * 按频道拉取符合条件的 Outbox 消息。
     *
     * @param channel       消息频道标识
     * @param availableTime 当前可发布的时间基准，通常为调度触发时间
     * @param limit         最大拉取数量，避免一次性加载过多消息
     * @return 待发布的消息列表，按实现约定排序；若无可发布消息返回空列表
     */
    List<OutboxMessage> fetchPending(String channel, Instant availableTime, int limit);

    /**
     * 尝试为指定 Outbox 消息抢占租约并进入发布中状态。
     *
     * @param id              Outbox 主键
     * @param expectedVersion 当前期望版本号（乐观锁），允许为空表示不做版本校验
     * @param leaseOwner      租约持有者标识，通常包含调度实例信息
     * @param leaseExpireAt   租约到期时间，到期后其他实例可再次抢占
     * @return 抢占成功返回 {@code true}，失败（版本冲突或状态变更）返回 {@code false}
     */
    boolean acquireLease(Long id, Long expectedVersion, String leaseOwner, Instant leaseExpireAt);

    /**
     * 发布成功后回写状态与第三方消息 ID。
     *
     * @param id              Outbox 主键
     * @param expectedVersion 当前期望版本号
     * @param messageId       下游消息系统返回的消息 ID，允许为空
     */
    void markPublished(Long id, Long expectedVersion, String messageId);

    /**
     * 发布失败但仍允许重试时，退回待发布状态并安排下一次尝试时间。
     *
     * @param id              Outbox 主键
     * @param expectedVersion 当前期望版本号
     * @param retryCount      已尝试次数
     * @param nextRetryAt     下一次允许重试的时间点
     * @param errorCode       错误码，用于分类统计
     * @param errorMessage    错误描述
     */
    void markDeferred(Long id,
                      Long expectedVersion,
                      int retryCount,
                      Instant nextRetryAt,
                      String errorCode,
                      String errorMessage);

    /**
     * 发布失败且超过重试上限时，标记为 DEAD，后续需人工或异步补偿。
     *
     * @param id              Outbox 主键
     * @param expectedVersion 当前期望版本号
     * @param retryCount      已尝试次数
     * @param errorCode       错误码
     * @param errorMessage    错误描述
     */
    void markFailed(Long id,
                    Long expectedVersion,
                    int retryCount,
                    String errorCode,
                    String errorMessage);
}
