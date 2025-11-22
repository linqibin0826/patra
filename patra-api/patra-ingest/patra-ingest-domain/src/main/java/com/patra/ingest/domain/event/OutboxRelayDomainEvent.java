package com.patra.ingest.domain.event;

import com.patra.common.domain.DomainEvent;

/// Outbox 中继领域事件标记接口。
/// 
/// 语义:在 Outbox 中继循环(获取 → 租约 → 发布 →
/// 持久化)期间产生的事件,并且旨在供外部消费者(监控、审计、异步下游)使用,必须实现此接口。通用标记让事件总线和监听器可以方便地分组或过滤事件。
/// 
/// 设计指南:
/// 
/// - 事件类型应依赖不可变记录或只读字段,以使负载保持稳定。
///   - 字段名称应镜像 Outbox 表和发布上下文,以减少解释工作。
///   - 每个实现必须公开 `occurredAt` 字段;当缺失时,在构造期间提供默认时间戳。
/// 
public interface OutboxRelayDomainEvent extends DomainEvent {}
