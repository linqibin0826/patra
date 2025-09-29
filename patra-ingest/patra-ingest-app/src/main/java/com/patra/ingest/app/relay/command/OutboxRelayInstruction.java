package com.patra.ingest.app.relay.command;

import java.time.Duration;
import java.time.Instant;

/**
 * 外部调度触发的 Relay 指令，可覆盖默认参数（为空时由配置回退）。
 * <p>字段说明：
 * <ul>
 *   <li>channel：目标 Outbox 频道；为空使用默认频道。</li>
 *   <li>triggeredAt：触发时间基准；为空取当前时间。</li>
 *   <li>batchSize：拉取待发布消息的最大条数；<=0 或 null 回退默认。</li>
 *   <li>leaseDuration：单条消息租约时长；null/非正值回退默认。</li>
 *   <li>maxAttempts：最大尝试次数（含首次）；null/<=0 回退默认。</li>
 *   <li>initialBackoff：首次重试等待时长。</li>
 *   <li>leaseOwner：租约持有者标识；为空时由 PlanBuilder 动态生成（含 host+时间戳+uuid）。</li>
 * </ul>
 * 不变式：该对象为不可变结构，供后续构建 {@code RelayPlan} 使用。
 */
public record OutboxRelayInstruction(
        com.patra.ingest.domain.messaging.ChannelKey channel,
        Instant triggeredAt,
        Integer batchSize,
        Duration leaseDuration,
        Integer maxAttempts,
        Duration initialBackoff,
        String leaseOwner
) { }
