package com.patra.ingest.domain.event;

import java.time.Instant;

/**
 * 消息重试计划事件。
 *
 * <p>触发：消息发送失败且被判定为可重试（未超阈值 / 错误可恢复）时，记录下一次重试计划并发布。</p>
 * <p>用途：
 * <ul>
 *   <li>调度：对重试堆积率进行观察，优化 backoff 策略。</li>
 *   <li>链路诊断：分析 errorCode/次数分布判断依赖稳定性。</li>
 * </ul>
 * </p>
 */
public record OutboxMessageDeferredEvent(
        /** 消息主键。 */
        Long messageId,
        /** 逻辑通道。 */
        String channel,
        /** 下一次重试将使用的 retryCount（= 当前失败次数 + 1）。 */
        int nextRetryCount,
        /** 计划重试时间。 */
        Instant nextRetryAt,
        /** 本次失败的错误代码。 */
        String errorCode,
        /** 本次失败摘要信息。 */
        String errorMessage,
        /** 事件发生时间。 */
        Instant occurredAt
) implements OutboxRelayDomainEvent {
}
