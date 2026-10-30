package dev.patra.interaction.support;

import dev.patra.interaction.api.discussion.event.CommentDeleted;
import dev.patra.interaction.api.discussion.event.CommentEdited;
import dev.patra.interaction.api.discussion.event.CommentPublished;
import dev.patra.interaction.api.post.event.PostDeleted;
import dev.patra.interaction.api.reaction.event.HelpfulMarked;
import dev.patra.interaction.api.subject.event.InteractionExchange;
import java.util.ArrayList;
import java.util.List;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/// 测试队列：替通知服务绑定 interaction 外送的五个路由键（PAP-141）。
///
/// 外送带 `mandatory`，没有队列可投的事件会被退回、在登记表里积着，所以共用上下文的所有流程测试都要它；事件流程测试
/// 再从这里收消息核对契约。每例前由 {@link InteractionFlowITSupport} 清空。
@TestConfiguration
public class InteractionEventsITSpringConfig {

  /// 测试队列名。
  public static final String QUEUE = "interaction.it.events";

  /// 外送的五个路由键。
  public static final List<String> ROUTING_KEYS =
      List.of(
          CommentPublished.ROUTING_KEY,
          CommentEdited.ROUTING_KEY,
          CommentDeleted.ROUTING_KEY,
          PostDeleted.ROUTING_KEY,
          HelpfulMarked.ROUTING_KEY);

  /// 测试队列与它在 `interaction.events` 上的五个绑定。持久化：RabbitMQ 4 不再允许非独占的临时队列。交换机由应用自己声明。
  ///
  /// @return 声明
  @Bean
  Declarables interactionItEventsQueue() {
    List<Declarable> declarables = new ArrayList<>();
    declarables.add(new Queue(QUEUE));
    for (String key : ROUTING_KEYS) {
      declarables.add(
          new Binding(QUEUE, Binding.DestinationType.QUEUE, InteractionExchange.NAME, key, null));
    }
    return new Declarables(declarables);
  }
}
