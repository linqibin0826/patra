package com.patra.ingest.adapter.inbound.scheduler.param;

/**
 * Outbox Relay 任务参数（由 XXL-Job JSON 传入）。
 * <p>所有字段可选，空值由业务层使用默认配置回退：</p>
 * <ul>
 *   <li>channel：消息通道；为空使用配置默认通道。</li>
 *   <li>batchSize：单次抓取数量；为空采用默认批次（配置决定）。</li>
 *   <li>leaseDuration：租约持续时间；支持 ISO-8601（PT15S）或纯秒数；为空使用默认。</li>
 *   <li>maxAttempts：最大尝试次数（含首次）；为空使用默认（通常 >=3）。</li>
 *   <li>initialBackoff：初始退避时长；同 leaseDuration 格式。</li>
 * </ul>
 */
public record OutboxRelayJobParam(
        String channel,
        Integer batchSize,
        String leaseDuration,
        Integer maxAttempts,
        String initialBackoff
) { }
