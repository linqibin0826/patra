package dev.linqibin.patra.ingest.domain.event;

import java.time.Instant;

/// Outbox 消息成功发布领域事件,当 Outbox 消息成功发布时触发。
///
/// 触发条件:在 Outbox 记录交付到消息代理并标记为 `PUBLISHED` 后立即触发。
///
/// 用途:
///
/// - 指标:测量每个通道的成功率和分区分布
///   - 审计:跟踪成功的消息发布以进行监控和告警
///   - 下游:可选消费者可以构建第二阶段扇出或填充缓存
///
/// 幂等性:每个 `messageId` 应只触发一次此事件;需要额外幂等性的监听器可以重用 `messageId` 作为其键。
public record OutboxMessagePublishedEvent(
    /// Outbox 记录的主标识符。
    Long messageId,
    /// 发布期间使用的逻辑通道(主题/流)。
    String channel,
    /// 分区路由键,可能由代理进行哈希。
    String partitionKey,
    /// 事件发生的 UTC 时间戳。
    Instant occurredAt)
    implements OutboxRelayDomainEvent {}
