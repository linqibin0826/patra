package dev.patra.tool.events.support;

import dev.patra.tool.events.consumer.EventQueue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.QueueBuilder.Overflow;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.RoutingTarget;

/// 集成测试的事件装配，相当于服务 boot 层的 `EventExternalizationConfiguration` 与队列声明。
@Configuration(proxyBeanMethods = false)
public class EventsITSpringConfig {

  /// 发布方的主题交换机。
  public static final String EXCHANGE = "events-it.events";

  /// 订单队列收的路由键。
  public static final String ORDER_PLACED = "order.placed";

  /// 绑到一个拒收队列的路由键：broker 回 nack。
  public static final String ORDER_REJECTED = "order.rejected";

  /// 没有任何队列绑定的路由键：消息被退回。
  public static final String ORDER_NOWHERE = "order.nowhere";

  /// 消费方的订单队列。
  public static final String ORDERS_QUEUE = "events-it.orders";

  /// 订单队列的死信队列。
  public static final String ORDERS_DLQ = ORDERS_QUEUE + ".dlq";

  /// 长度上限 0、超限拒收的队列。
  public static final String FULL_QUEUE = "events-it.full";

  /// 选出要外送的事件、路由与映射。
  ///
  /// @return 外送配置
  @Bean
  EventExternalizationConfiguration eventExternalizationConfiguration() {
    return EventExternalizationConfiguration.externalizing()
        .selectByType(EventsITOrderPlaced.class)
        .route(
            EventsITOrderPlaced.class,
            event -> RoutingTarget.forTarget(EXCHANGE).andKey(event.routingKey()))
        .mapping(
            EventsITOrderPlaced.class,
            event ->
                new EventsITOrderPlacedMessage(event.eventId(), event.orderNo(), event.poison()))
        .build();
  }

  /// 发布方声明自己的交换机。
  ///
  /// @return 交换机
  @Bean
  TopicExchange eventsItExchange() {
    return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
  }

  /// 订单队列：带死信对，绑定订单路由键。
  ///
  /// @return 队列声明
  @Bean
  Declarables ordersQueue() {
    return EventQueue.named(ORDERS_QUEUE).bind(EXCHANGE, ORDER_PLACED).declarables();
  }

  /// 一直拒收的队列：长度上限 0、超限时 broker 对发布方回 nack。
  ///
  /// @return 队列与绑定
  @Bean
  Declarables fullQueue() {
    Queue full =
        QueueBuilder.durable(FULL_QUEUE).maxLength(0).overflow(Overflow.rejectPublish).build();
    Binding binding =
        BindingBuilder.bind(full)
            .to(ExchangeBuilder.topicExchange(EXCHANGE).durable(true).<TopicExchange>build())
            .with(ORDER_REJECTED);
    return new Declarables(full, binding);
  }
}
