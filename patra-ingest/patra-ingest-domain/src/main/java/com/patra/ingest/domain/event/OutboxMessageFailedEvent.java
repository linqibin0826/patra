package com.patra.ingest.domain.event;

import java.time.Instant;

/**
 * 消息被标记为 DEAD（终止重试）事件。
 *
 * <p>触发：达到最大重试次数或被策略判定不可恢复（例如格式永久不兼容）。</p>
 * <p>用途：
 * <ul>
 *   <li>告警：监控 errorCode 聚合，快速定位热点故障。</li>
 *   <li>补偿：可驱动人工或离线重播工具针对 DEAD 消息做二次尝试。</li>
 * </ul>
 * </p>
 */
public record OutboxMessageFailedEvent(
        /** 消息主键。 */
        Long messageId,
        /** 逻辑通道。 */
        String channel,
        /** 最终失败前已发生的重试次数。 */
        int retryCount,
        /** 错误代码（便于聚合统计）。 */
        String errorCode,
        /** 最终失败的摘要信息。 */
        String errorMessage,
        /** 事件发生时间。 */
        Instant occurredAt
) implements OutboxRelayDomainEvent {
}
