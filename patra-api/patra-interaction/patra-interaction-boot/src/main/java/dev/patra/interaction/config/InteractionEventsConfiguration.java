package dev.patra.interaction.config;

import dev.patra.interaction.api.discussion.event.CommentDeleted;
import dev.patra.interaction.api.discussion.event.CommentEdited;
import dev.patra.interaction.api.discussion.event.CommentPublished;
import dev.patra.interaction.api.post.event.PostDeleted;
import dev.patra.interaction.api.reaction.event.HelpfulMarked;
import dev.patra.interaction.api.subject.event.InteractionExchange;
import dev.patra.interaction.domain.discussion.event.CommentDeletedEvent;
import dev.patra.interaction.domain.discussion.event.CommentEditedEvent;
import dev.patra.interaction.domain.discussion.event.CommentPublishedEvent;
import dev.patra.interaction.domain.post.event.PostDeletedEvent;
import dev.patra.interaction.domain.reaction.event.HelpfulMarkedEvent;
import java.util.Set;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.RoutingTarget;

/// interaction 的集成事件外送（PAP-141，v0.16 决策 B、C）：选中哪些领域事件、发到哪个交换机、带什么路由键、映射成
/// `patra-interaction-api` 里的哪个 record。领域层不贴 `@Externalized`，外送只在这里配置。
///
/// 只外送有队列订阅的事件：事件模块发送时带 `mandatory`，没有队列可投的消息被 broker 退回、登记行每分钟补投、永远发
/// 不出去。所以「有帮助已取消」不外送（通知服务对它没有动作）。新增外送事件时，通知服务要先绑定它的路由键。
///
/// 上线后改外送领域事件的类名或字段，先确认 `event_publication` 是空的（`patra-tool-events` README）。
@Configuration(proxyBeanMethods = false)
public class InteractionEventsConfiguration {

  /// 外送的领域事件。
  static final Set<Class<?>> EXTERNALIZED =
      Set.of(
          CommentPublishedEvent.class,
          CommentEditedEvent.class,
          CommentDeletedEvent.class,
          PostDeletedEvent.class,
          HelpfulMarkedEvent.class);

  /// interaction 的事件交换机：topic、持久化。消费方也声明一次，谁先部署都行。
  ///
  /// @return 交换机
  @Bean
  public TopicExchange interactionEventsExchange() {
    return ExchangeBuilder.topicExchange(InteractionExchange.NAME).durable(true).build();
  }

  /// 外送配置：五种领域事件，路由键为事件类型。
  ///
  /// @return 配置
  @Bean
  public EventExternalizationConfiguration eventExternalizationConfiguration() {
    return EventExternalizationConfiguration.externalizing()
        .selectByType(EXTERNALIZED::contains)
        .route(CommentPublishedEvent.class, event -> target(CommentPublished.ROUTING_KEY))
        .mapping(CommentPublishedEvent.class, IntegrationEventMapper::commentPublished)
        .route(CommentEditedEvent.class, event -> target(CommentEdited.ROUTING_KEY))
        .mapping(CommentEditedEvent.class, IntegrationEventMapper::commentEdited)
        .route(CommentDeletedEvent.class, event -> target(CommentDeleted.ROUTING_KEY))
        .mapping(CommentDeletedEvent.class, IntegrationEventMapper::commentDeleted)
        .route(PostDeletedEvent.class, event -> target(PostDeleted.ROUTING_KEY))
        .mapping(PostDeletedEvent.class, IntegrationEventMapper::postDeleted)
        .route(HelpfulMarkedEvent.class, event -> target(HelpfulMarked.ROUTING_KEY))
        .mapping(HelpfulMarkedEvent.class, IntegrationEventMapper::helpfulMarked)
        .build();
  }

  /// 发到 interaction 交换机、带这个路由键。
  ///
  /// @param routingKey 路由键
  /// @return 目标
  private static RoutingTarget target(String routingKey) {
    return RoutingTarget.forTarget(InteractionExchange.NAME).andKey(routingKey);
  }
}
