package com.patra.ingest.domain.event;

import java.time.Instant;

/// Outbox 消息永久失败领域事件,当消息被宣告为死信且停止进一步重试时触发。
/// 
/// 触发条件:达到重试限制或策略确定失败不可恢复(例如,永久不兼容的负载格式)后触发。
/// 
/// 用途:
/// 
/// - 告警:按错误代码聚合以快速定位热点失败模式
///   - 补偿:驱动手动或离线重放工具以检查死信消息
/// 
public record OutboxMessageFailedEvent(
    /// Outbox 消息的标识符。
    Long messageId,
    /// 消息的逻辑通道。
    String channel,
    /// 最终失败前发生的重试次数。
    int retryCount,
    /// 与终态失败关联的错误代码。
    String errorCode,
    /// 描述终态失败的摘要消息。
    String errorMessage,
    /// 事件发生的时间戳。
    Instant occurredAt)
    implements OutboxRelayDomainEvent {}
