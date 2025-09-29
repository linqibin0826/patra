package com.patra.ingest.adapter.inbound.scheduler.param;

/**
 * Outbox Relay 任务参数。
 *
 * @param channel 指定 outbox channel
 * @param batchSize 拉取批次大小
 * @param leaseDuration 租约持续时间（ISO8601 或秒）
 * @param maxAttempts 最大尝试次数（包含第一次）
 * @param initialBackoff 初始退避时长
 */
public record OutboxRelayJobParam(
        String channel,
        Integer batchSize,
        String leaseDuration,
        Integer maxAttempts,
        String initialBackoff
) {
}
