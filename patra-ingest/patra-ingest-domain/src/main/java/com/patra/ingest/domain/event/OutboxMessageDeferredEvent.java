package com.patra.ingest.domain.event;

import java.time.Instant;

/// Outbox 消息延迟重试领域事件,记录 Outbox 消息的计划重试。
///
/// 触发条件:发布失败但被归类为可重试(未超过阈值且错误可恢复)时触发,以便捕获下一次重试计划。
///
/// 用途:
///
/// - 调度管理:观察重试积压趋势以调整退避策略
///   - 管道诊断:分析错误代码和重试次数以评估下游稳定性
///
public record OutboxMessageDeferredEvent(
    /// 计划重放的消息标识符。
    Long messageId,
    /// 逻辑 Outbox 通道。
    String channel,
    /// 下一次尝试将使用的重试次数(当前失败次数 + 1)。
    int nextRetryCount,
    /// 计划的重试时间戳。
    Instant nextRetryAt,
    /// 上次失败产生的错误代码。
    String errorCode,
    /// 失败的摘要消息。
    String errorMessage,
    /// 事件触发的时间戳。
    Instant occurredAt)
    implements OutboxRelayDomainEvent {}
