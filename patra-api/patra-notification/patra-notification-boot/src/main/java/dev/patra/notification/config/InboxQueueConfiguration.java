package dev.patra.notification.config;

import dev.patra.interaction.api.discussion.event.CommentDeleted;
import dev.patra.interaction.api.discussion.event.CommentEdited;
import dev.patra.interaction.api.discussion.event.CommentPublished;
import dev.patra.interaction.api.post.event.PostDeleted;
import dev.patra.interaction.api.reaction.event.HelpfulMarked;
import dev.patra.interaction.api.subject.event.InteractionExchange;
import dev.patra.notification.app.inbox.usecase.ingest.InboxConsumer;
import dev.patra.tool.events.consumer.EventQueue;
import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/// 信箱队列的声明（PAP-143）：`notification.inbox` 与它的死信交换机、死信队列，绑定 interaction 交换机的五个路由键
/// （评论发表 / 编辑 / 删除、经验删除、有帮助已点）。由 `RabbitAdmin` 在连接时声明，不依赖 interaction 先部署。
@Configuration
public class InboxQueueConfiguration {

  /// 队列与绑定。
  ///
  /// @return 声明
  @Bean
  public Declarables inboxQueue() {
    return EventQueue.named(InboxConsumer.NAME)
        .bind(
            InteractionExchange.NAME,
            CommentPublished.ROUTING_KEY,
            CommentEdited.ROUTING_KEY,
            CommentDeleted.ROUTING_KEY,
            PostDeleted.ROUTING_KEY,
            HelpfulMarked.ROUTING_KEY)
        .declarables();
  }
}
