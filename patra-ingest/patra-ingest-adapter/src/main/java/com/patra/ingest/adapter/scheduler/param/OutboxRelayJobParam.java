package com.patra.ingest.adapter.scheduler.param;

/**
 * Outbox Relay 任务参数(通过 XXL-Job JSON 传递)。
 *
 * <p>所有字段都是可选的;空值将回退到业务层的默认值。
 *
 * @param channel 消息通道;空白时使用配置的默认值
 * @param batchSize 每次尝试获取的记录数;空白时使用配置的默认值
 * @param leaseDuration 租约持续时间;支持 ISO-8601 格式(如 PT15S)或纯秒数;空白时使用默认值
 * @param maxAttempts 最大尝试次数(包括首次);空白时使用默认值(通常 >= 3)
 * @param initialBackoff 初始退避持续时间;格式同 leaseDuration
 */
public record OutboxRelayJobParam(
    String channel,
    Integer batchSize,
    String leaseDuration,
    Integer maxAttempts,
    String initialBackoff) {}
