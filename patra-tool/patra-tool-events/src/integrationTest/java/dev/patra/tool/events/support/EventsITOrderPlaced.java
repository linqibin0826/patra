package dev.patra.tool.events.support;

import java.util.UUID;

/// 测试用的领域事件：下了一单。`eventId` 在事件创建时生成，补投时随登记表原样还原。
///
/// @param eventId 事件 ID
/// @param orderNo 订单号
/// @param routingKey 外送的路由键，用例借它把事件送到不同的队列或无人订阅的键
/// @param poison 消费方处理它时一直失败
public record EventsITOrderPlaced(UUID eventId, String orderNo, String routingKey, boolean poison) {

  /// 正常下单、送到订单队列的事件。
  ///
  /// @param orderNo 订单号
  /// @return 事件
  public static EventsITOrderPlaced of(String orderNo) {
    return routedTo(orderNo, EventsITSpringConfig.ORDER_PLACED);
  }

  /// 送到指定路由键的事件。
  ///
  /// @param orderNo 订单号
  /// @param routingKey 路由键
  /// @return 事件
  public static EventsITOrderPlaced routedTo(String orderNo, String routingKey) {
    return new EventsITOrderPlaced(UUID.randomUUID(), orderNo, routingKey, false);
  }

  /// 消费方一直处理失败的事件。
  ///
  /// @param orderNo 订单号
  /// @return 事件
  public static EventsITOrderPlaced poison(String orderNo) {
    return new EventsITOrderPlaced(
        UUID.randomUUID(), orderNo, EventsITSpringConfig.ORDER_PLACED, true);
  }
}
