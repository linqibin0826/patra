package com.patra.ingest.domain.model.vo.relay;

import com.patra.common.messaging.ChannelKey;
import com.patra.ingest.domain.event.OutboxRelayDomainEvent;
import java.util.Collections;
import java.util.List;

/**
 * 中继批次执行结果汇总 Value Object。
 *
 * <p>记录单次中继批次执行的统计信息和生成的领域事件。
 *
 * <p><b>业务语义:</b>
 *
 * <ul>
 *   <li>fetched - 从 Outbox 表获取的消息数
 *   <li>published - 成功发布到 MQ 的消息数
 *   <li>retried - 触发重试的消息数
 *   <li>failed - 失败的消息数
 *   <li>leaseMissed - 租约丢失的消息数
 *   <li>events - 批次执行过程中产生的领域事件(不可变列表)
 * </ul>
 *
 * <p><b>不变性:</b> events 列表在构造时进行防御性复制,保证 Immutable。
 *
 * @param channel 通道键(标识消息通道)
 * @param fetched 获取的消息数
 * @param published 发布成功的消息数
 * @param retried 重试的消息数
 * @param failed 失败的消息数
 * @param leaseMissed 租约丢失的消息数
 * @param events 领域事件列表(不可变)
 * @author Papertrace Team
 * @since 2.0
 */
public record RelayBatchResult(
    ChannelKey channel,
    int fetched,
    int published,
    int retried,
    int failed,
    int leaseMissed,
    List<OutboxRelayDomainEvent> events) {
  public RelayBatchResult {
    events = events == null ? List.of() : List.copyOf(events);
  }

  /** 工厂方法: 创建空的批次结果(所有计数为 0)。 */
  public static RelayBatchResult empty(ChannelKey channel) {
    return new RelayBatchResult(channel, 0, 0, 0, 0, 0, Collections.emptyList());
  }
}
