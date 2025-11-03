package com.patra.ingest.app.usecase.relay.dto;

import com.patra.common.messaging.ChannelKey;

/**
 * 单批次中继的聚合指标
 *
 * <p>字段语义:
 *
 * <ul>
 *   <li>{@code channel}: 本轮处理的 Outbox 通道
 *   <li>{@code fetched}: 批次中尝试的消息总数 (包括租约丢失的消息)
 *   <li>{@code published}: 成功发布并标记完成的消息数
 *   <li>{@code retried}: 延迟重试的消息数
 *   <li>{@code failed}: 标记为永久失败的消息数
 *   <li>{@code leaseMissed}: 因其他实例获取租约而跳过的消息数
 * </ul>
 *
 * 用途: 日志记录、调度器仪表板和指标聚合
 */
public record RelayReport(
    ChannelKey channel, int fetched, int published, int retried, int failed, int leaseMissed) {
  /** 当功能禁用时使用的空报告 */
  public static RelayReport empty(ChannelKey channel) {
    return new RelayReport(channel, 0, 0, 0, 0, 0);
  }
}
