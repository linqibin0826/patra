package com.patra.ingest.domain.event;

import java.time.Instant;

/**
 * 租约抢占失败事件。
 *
 * <p>触发：多个 Relay 实例竞争同一消息租约（乐观锁/条件更新）失败时发布，用于观测竞争程度。</p>
 * <p>用途：
 * <ul>
 *   <li>扩缩容决策：大量租约竞争可能表明并行度冗余。</li>
 *   <li>热点分析：可按 channel + messageId 聚合识别热点消息。</li>
 * </ul>
 * </p>
 */
public record OutboxLeaseMissedEvent(
        /** 目标消息主键。 */
        Long messageId,
        /** 通道。 */
        String channel,
        /** 当前实例申请使用的租约 owner 标识。 */
        String requestedLeaseOwner,
        /** 数据库中已有的租约 owner。 */
        String currentLeaseOwner,
        /** 事件发生时间。 */
        Instant occurredAt
) implements OutboxRelayDomainEvent {
}
