package com.patra.ingest.adapter.scheduler.param;

/// Outbox 消息中继任务参数记录。
/// 
/// 通过 XXL-Job 调度器以 JSON 格式传递的任务参数。所有字段均为可选,未提供的参数将回退到应用层配置的默认值。
/// 
/// 参数说明:
/// 
/// @param channel 消息通道标识符;为空时使用全局配置的默认通道
/// @param batchSize 每次扫描获取的 Outbox 记录批量大小;为空时使用默认批量大小(通常为 100)
/// @param leaseDuration 消息租约持续时间;支持 ISO-8601 格式(如 PT15S)或纯秒数;为空时使用默认租约时长
/// @param maxAttempts 最大重试次数(包括首次尝试);为空时使用默认值(通常 >= 3),超过后消息进入死信队列
/// @param initialBackoff 重试初始退避时间;格式同 leaseDuration;为空时使用默认退避策略
public record OutboxRelayJobParam(
    String channel,
    Integer batchSize,
    String leaseDuration,
    Integer maxAttempts,
    String initialBackoff) {}
