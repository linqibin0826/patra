package dev.patra.notification.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;

/// 信箱队列的绑定：interaction 外送的五个路由键都要绑上，缺一个那类事件就会被 broker 退回、在 interaction 无限补投。
class InboxQueueConfigurationTest {

  /// 恰好五个：评论发表 / 编辑 / 删除、经验删除、有帮助已点；有帮助取消不外送，不绑。
  @Test
  void inbox_queue_binds_five_routing_keys() {
    assertThat(
            new InboxQueueConfiguration()
                .inboxQueue().getDeclarablesByType(Binding.class).stream()
                    .filter(binding -> "interaction.events".equals(binding.getExchange()))
                    .map(Binding::getRoutingKey))
        .containsExactlyInAnyOrder(
            "comment.published",
            "comment.edited",
            "comment.deleted",
            "post.deleted",
            "helpful.marked");
  }

  /// 信箱靠顺序处理保证正确：跑多台时队列同一时刻只投给一个实例。
  @Test
  void should_declare_inbox_queue_as_single_active_consumer() {
    Queue inbox =
        new InboxQueueConfiguration()
            .inboxQueue().getDeclarablesByType(Queue.class).stream()
                .filter(queue -> queue.getName().equals("notification.inbox"))
                .findFirst()
                .orElseThrow();

    assertThat(inbox.getArguments()).containsEntry("x-single-active-consumer", true);
  }
}
