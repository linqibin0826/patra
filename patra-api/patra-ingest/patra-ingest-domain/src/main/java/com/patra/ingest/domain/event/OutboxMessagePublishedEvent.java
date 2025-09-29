package com.patra.ingest.domain.event;

import java.time.Instant;

/**
 * 消息发布成功事件。
 *
 * <p>触发：Outbox 消息成功发送至消息中枢并被标记为 PUBLISHED 后立即发布。</p>
 * <p>用途：
 * <ul>
 *   <li>指标：统计各 channel 成功率、分区分布。</li>
 *   <li>审计：关联 messageId 与 brokerMessageId 追踪链路。</li>
 *   <li>下游：可选消费做二级分发或缓存填充。</li>
 * </ul>
 * </p>
 * <p>幂等性：同一 messageId 理论上仅发布一次；若监听器侧需防重，可用 messageId 作为幂等键。</p>
 */
public record OutboxMessagePublishedEvent(
        /** Outbox 表中的消息主键。 */
        Long messageId,
        /** 发布使用的逻辑通道（主题 / topic / stream）。 */
        String channel,
        /** 分区路由键（可能被消息中枢哈希）。 */
        String partitionKey,
        /** 消息中枢返回的消息 ID（便于跨系统追踪）。 */
        String brokerMessageId,
        /** 事件发生时间（UTC）。 */
        Instant occurredAt
) implements OutboxRelayDomainEvent {
}
