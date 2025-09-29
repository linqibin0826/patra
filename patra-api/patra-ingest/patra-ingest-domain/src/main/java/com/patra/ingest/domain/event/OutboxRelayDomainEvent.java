package com.patra.ingest.domain.event;

import com.patra.common.domain.DomainEvent;

/**
 * Outbox Relay 领域事件标记接口。
 *
 * <p>语义：凡由 Outbox Relay 执行循环（拉取→租约→发布→落库）过程中产生且希望对外（监控/审计/异步消费）暴露的事件，需实现本接口。
 * 统一标记便于事件总线/监听器进行分组过滤。</p>
 * <p>设计约定：
 * <ul>
 *   <li>事件对象使用不可变 record 或只读字段，确保发布后不会再被修改。</li>
 *   <li>字段命名保持与 Outbox 表/消息投递上下文一致，降低心智成本。</li>
 *   <li>所有实现需包含 occurredAt 字段，缺失则在构造侧补默认时间。</li>
 * </ul>
 * </p>
 */
public interface OutboxRelayDomainEvent extends DomainEvent {
}
