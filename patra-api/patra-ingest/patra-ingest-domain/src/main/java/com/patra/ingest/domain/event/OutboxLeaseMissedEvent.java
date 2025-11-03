package com.patra.ingest.domain.event;

import java.time.Instant;

/**
 * Outbox 租约竞争失败领域事件,当无法获取 Outbox 消息租约时触发。
 *
 * <p>触发条件:多个中继实例竞争同一消息租约时,乐观锁更新失败后触发。用于观察租约竞争水平。
 *
 * <p>用途:
 *
 * <ul>
 *   <li>扩展决策:重复的租约冲突可能表明并发度过高
 *   <li>热点分析:按 {@code channel + messageId} 聚合以发现热点消息
 * </ul>
 */
public record OutboxLeaseMissedEvent(
    /** 触发租约冲突的消息标识符。 */
    Long messageId,
    /** 与消息关联的 Outbox 通道。 */
    String channel,
    /** 当前中继实例请求的租约所有者。 */
    String requestedLeaseOwner,
    /** 数据库中已记录的租约所有者。 */
    String currentLeaseOwner,
    /** 事件发生的时间戳。 */
    Instant occurredAt)
    implements OutboxRelayDomainEvent {}
