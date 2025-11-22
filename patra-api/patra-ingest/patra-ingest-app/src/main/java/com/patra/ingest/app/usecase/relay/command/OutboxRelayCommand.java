package com.patra.ingest.app.usecase.relay.command;

import com.patra.common.messaging.ChannelKey;
import java.time.Duration;
import java.time.Instant;

/// 由外部调度器触发的中继指令;可选字段覆盖默认值 (空值回退到配置)
/// 
/// 字段说明:
/// 
/// - `channel`: 目标 Outbox 通道; `null` 让计划选择配置的默认值或所有通道
///   - `triggeredAt`: 触发时间戳; `null` 解析为当前时间
///   - `batchSize`: 批次中获取的待处理消息最大数量; `null` 或 `<= 0` 恢复为默认值
///   - `leaseDuration`: 每条消息的租约持续时间; `null` 或非正数回退到默认值
///   - `maxAttempts`: 最大尝试次数 (包括首次运行); `null` 或 `<= 0` 使用默认值
///   - `initialBackoff`: 首次重试前的初始延迟
///   - `leaseOwner`: 租约持有者标识符; 空白时由计划构建器生成 (主机 + epoch 毫秒 + UUID)
/// 
/// 不可变契约,由 {@link RelayPlan} 构造使用
public record OutboxRelayCommand(
    ChannelKey channel,
    Instant triggeredAt,
    Integer batchSize,
    Duration leaseDuration,
    Integer maxAttempts,
    Duration initialBackoff,
    String leaseOwner) {}
