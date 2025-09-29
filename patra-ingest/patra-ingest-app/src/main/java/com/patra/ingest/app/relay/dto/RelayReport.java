package com.patra.ingest.app.relay.dto;

/**
 * Relay 执行统计结果（单次批量发布维度）。
 * <p>字段语义：
 * <ul>
 *   <li>channel：本次操作的 Outbox 频道。</li>
 *   <li>fetched：本批次尝试处理的消息条数（含租约失败的）。</li>
 *   <li>published：成功发布并标记完成的条数。</li>
 *   <li>retried：被延期以待重试的条数。</li>
 *   <li>failed：判定为永久失败的条数。</li>
 *   <li>leaseMissed：租约竞争失败（被其他实例占用）的条数。</li>
 * </ul>
 * 用途：日志观测 / 调度平台展示 / 指标上报聚合。
 */
public record RelayReport(
        com.patra.ingest.domain.messaging.ChannelKey channel,
        int fetched,
        int published,
        int retried,
        int failed,
        int leaseMissed
) {
    /** 返回空统计（用于功能关闭场景）。 */
    public static RelayReport empty(com.patra.ingest.domain.messaging.ChannelKey channel) {
        return new RelayReport(channel, 0, 0, 0, 0, 0);
    }
}
