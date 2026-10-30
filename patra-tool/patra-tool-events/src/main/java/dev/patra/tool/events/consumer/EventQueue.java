package dev.patra.tool.events.consumer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;

/// 消费队列的声明：队列本身、它的死信交换机与死信队列、到发布方主题交换机的绑定。
///
/// 服务把 {@link #declarables()} 注册成 bean，由 `RabbitAdmin` 在连接时声明：
///
/// ```java
/// @Bean
/// Declarables inboxQueue() {
///   return EventQueue.named("notification.inbox")
///       .bind("interaction.events", "comment.published", "comment.deleted")
///       .declarables();
/// }
/// ```
///
/// 名字规则：死信交换机 `<队列>.dlx`（直连），死信队列 `<队列>.dlq`，死信路由键就是队列名。
/// 重试耗尽的消息被拒绝且不回队列，RabbitMQ 按队列参数把它转进死信队列。发布方的交换机也在这里声明一遍
/// （持久化主题交换机，与发布方的声明一致），绑定不依赖发布方先部署。
///
/// @param queue 队列名
/// @param bindings 交换机 → 路由键
public record EventQueue(String queue, Map<String, List<String>> bindings) {

  /// 防御性拷贝。
  public EventQueue {
    bindings = Map.copyOf(bindings);
  }

  /// 从队列名开始声明。
  ///
  /// @param queue 队列名
  /// @return 还没有绑定的声明
  public static EventQueue named(String queue) {
    return new EventQueue(queue, Map.of());
  }

  /// 把队列按路由键绑到发布方的主题交换机。
  ///
  /// @param exchange 发布方的主题交换机
  /// @param routingKeys 要收的路由键，至少一个
  /// @return 加上绑定的新声明
  /// @throws IllegalArgumentException 没给路由键
  public EventQueue bind(String exchange, String... routingKeys) {
    if (routingKeys.length == 0) {
      throw new IllegalArgumentException("绑定交换机 " + exchange + " 至少要给一个路由键");
    }
    Map<String, List<String>> merged = new LinkedHashMap<>(bindings);
    List<String> keys = new ArrayList<>(merged.getOrDefault(exchange, List.of()));
    keys.addAll(List.of(routingKeys));
    merged.put(exchange, List.copyOf(keys));
    return new EventQueue(queue, merged);
  }

  /// 生成全部声明。
  ///
  /// @return 队列、死信交换机、死信队列、发布方交换机与绑定
  public Declarables declarables() {
    String deadLetterExchange = queue + ".dlx";
    String deadLetterQueue = queue + ".dlq";
    Queue main =
        QueueBuilder.durable(queue)
            .deadLetterExchange(deadLetterExchange)
            .deadLetterRoutingKey(queue)
            .build();
    DirectExchange dlx = ExchangeBuilder.directExchange(deadLetterExchange).durable(true).build();
    Queue dlq = QueueBuilder.durable(deadLetterQueue).build();

    List<Declarable> declarables = new ArrayList<>(List.of(main, dlx, dlq));
    declarables.add(BindingBuilder.bind(dlq).to(dlx).with(queue));
    bindings.forEach(
        (exchangeName, keys) -> {
          TopicExchange exchange =
              ExchangeBuilder.topicExchange(exchangeName).durable(true).build();
          declarables.add(exchange);
          keys.forEach(key -> declarables.add(BindingBuilder.bind(main).to(exchange).with(key)));
        });
    return new Declarables(declarables);
  }
}
