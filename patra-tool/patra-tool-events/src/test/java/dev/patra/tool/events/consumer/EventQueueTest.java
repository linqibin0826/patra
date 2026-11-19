package dev.patra.tool.events.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

@DisplayName("消费队列声明")
class EventQueueTest {

  private final List<?> declarables =
      List.copyOf(
          EventQueue.named("notification.inbox")
              .bind("interaction.events", "comment.published", "comment.deleted")
              .declarables()
              .getDeclarables());

  @Test
  @DisplayName("队列带死信交换机与死信路由键，死信交换机与死信队列成对声明")
  void should_declare_queue_with_dead_letter_pair() {
    Queue queue = queue("notification.inbox");
    assertThat(queue.isDurable()).isTrue();
    assertThat(queue.getArguments())
        .containsEntry("x-dead-letter-exchange", "notification.inbox.dlx")
        .containsEntry("x-dead-letter-routing-key", "notification.inbox");
    assertThat(queue("notification.inbox.dlq").isDurable()).isTrue();
    assertThat(declarables)
        .filteredOn(DirectExchange.class::isInstance)
        .map(DirectExchange.class::cast)
        .singleElement()
        .satisfies(
            exchange -> {
              assertThat(exchange.getName()).isEqualTo("notification.inbox.dlx");
              assertThat(exchange.isDurable()).isTrue();
            });
    assertThat(binding("notification.inbox.dlq"))
        .singleElement()
        .satisfies(
            binding -> {
              assertThat(binding.getExchange()).isEqualTo("notification.inbox.dlx");
              assertThat(binding.getRoutingKey()).isEqualTo("notification.inbox");
            });
  }

  @Test
  @DisplayName("声明发布方的主题交换机，并按每个路由键绑定队列")
  void should_bind_queue_to_topic_exchange_by_each_routing_key() {
    assertThat(declarables)
        .filteredOn(TopicExchange.class::isInstance)
        .map(TopicExchange.class::cast)
        .singleElement()
        .satisfies(
            exchange -> {
              assertThat(exchange.getName()).isEqualTo("interaction.events");
              assertThat(exchange.isDurable()).isTrue();
            });
    assertThat(binding("notification.inbox"))
        .allSatisfy(binding -> assertThat(binding.getExchange()).isEqualTo("interaction.events"))
        .extracting(Binding::getRoutingKey)
        .containsExactlyInAnyOrder("comment.published", "comment.deleted");
  }

  @Test
  @DisplayName("绑定时必须给至少一个路由键")
  void should_reject_binding_without_routing_keys() {
    EventQueue queue = EventQueue.named("notification.inbox");

    assertThatThrownBy(() -> queue.bind("interaction.events"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("打开单活跃消费者：主队列带 x-single-active-consumer，死信队列不带")
  void should_mark_only_main_queue_single_active_consumer_when_enabled() {
    Declarables declared =
        EventQueue.named("q").bind("x", "k").singleActiveConsumer().declarables();

    assertThat(queueOf(declared, "q").getArguments())
        .containsEntry("x-single-active-consumer", true)
        .containsEntry("x-dead-letter-exchange", "q.dlx");
    assertThat(queueOf(declared, "q.dlq").getArguments())
        .doesNotContainKey("x-single-active-consumer");
  }

  @Test
  @DisplayName("默认不带 x-single-active-consumer")
  void should_not_set_single_active_consumer_by_default() {
    assertThat(queue("notification.inbox").getArguments())
        .doesNotContainKey("x-single-active-consumer");
  }

  @Test
  @DisplayName("先开开关再绑定，开关不丢")
  void should_keep_single_active_consumer_when_binding_afterwards() {
    Declarables declared =
        EventQueue.named("q").singleActiveConsumer().bind("x", "k").declarables();

    assertThat(queueOf(declared, "q").getArguments())
        .containsEntry("x-single-active-consumer", true);
  }

  /// 按名字找某次声明里的队列。
  ///
  /// @param declared 声明
  /// @param name 队列名
  /// @return 队列
  private static Queue queueOf(Declarables declared, String name) {
    return declared.getDeclarables().stream()
        .filter(Queue.class::isInstance)
        .map(Queue.class::cast)
        .filter(queue -> queue.getName().equals(name))
        .findFirst()
        .orElseThrow();
  }

  /// 按名字找声明里的队列。
  ///
  /// @param name 队列名
  /// @return 队列
  private Queue queue(String name) {
    return declarables.stream()
        .filter(Queue.class::isInstance)
        .map(Queue.class::cast)
        .filter(queue -> queue.getName().equals(name))
        .findFirst()
        .orElseThrow();
  }

  /// 找绑定到指定队列的绑定。
  ///
  /// @param queue 队列名
  /// @return 绑定
  private List<Binding> binding(String queue) {
    return declarables.stream()
        .filter(Binding.class::isInstance)
        .map(Binding.class::cast)
        .filter(binding -> binding.getDestination().equals(queue))
        .toList();
  }
}
