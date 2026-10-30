package dev.patra.tool.events.support;

import dev.patra.tool.core.event.IntegrationEvent;
import java.util.UUID;

/// 测试用的集成事件：外送到 RabbitMQ 的「下了一单」。
///
/// @param eventId 事件 ID，取自领域事件
/// @param orderNo 订单号
/// @param poison 消费方处理它时一直失败
public record EventsITOrderPlacedMessage(UUID eventId, String orderNo, boolean poison)
    implements IntegrationEvent {}
