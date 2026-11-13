package dev.patra.notification.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.patra.notification.infra.realtime.broadcast.DiscardOldestBroadcastPolicy;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/// 未读数广播的装配（spec 5.2、6.1、6.2）：专用线程池、fanout 交换机与每实例的临时队列、发布连接。
class UnreadBroadcastConfigurationTest {

  private final UnreadBroadcastConfiguration config = new UnreadBroadcastConfiguration();

  /// 1 个线程、排队 500，满了丢最旧的；线程名认得出是广播。
  @Test
  void should_build_single_thread_executor_with_bounded_queue() {
    ThreadPoolTaskExecutor executor = config.unreadBroadcastExecutor(Clock.systemUTC());
    executor.initialize();
    try {
      assertThat(executor.getCorePoolSize()).isEqualTo(1);
      assertThat(executor.getMaxPoolSize()).isEqualTo(1);
      assertThat(executor.getThreadNamePrefix()).isEqualTo("unread-broadcast-");
      assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(500);
      assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
          .isInstanceOf(DiscardOldestBroadcastPolicy.class);
    } finally {
      executor.shutdown();
    }
  }

  /// 持久化的 fanout 交换机；每个实例一条临时队列（非持久、独占、断开即删），名字带前缀便于在管理界面认出。
  @Test
  void should_declare_durable_fanout_exchange_and_private_queue_bound_to_it() {
    FanoutExchange exchange = config.unreadBroadcastExchange();
    AnonymousQueue queue = config.unreadBroadcastQueue();

    assertThat(exchange.getName()).isEqualTo("notification.unread.broadcast");
    assertThat(exchange.isDurable()).isTrue();
    assertThat(queue.getName()).startsWith("notification.unread.");
    assertThat(queue.isDurable()).isFalse();
    assertThat(queue.isExclusive()).isTrue();
    assertThat(queue.isAutoDelete()).isTrue();
    Binding binding = config.unreadBroadcastBinding(queue, exchange);
    assertThat(binding.getDestination()).isEqualTo(queue.getName());
    assertThat(binding.getExchange()).isEqualTo("notification.unread.broadcast");
  }

  /// 临时队列有上限：某个实例活着但广播消费卡住时，最多积 1000 条、满了丢最旧的，不会一直堆在 broker 内存里把它拖进
  /// 内存告警（那会阻塞所有服务的发布）。
  @Test
  void should_cap_broadcast_queue_and_drop_oldest() {
    assertThat(config.unreadBroadcastQueue().getArguments())
        .containsEntry("x-max-length", 1000)
        .containsEntry("x-overflow", "drop-head");
  }

  /// 每个 Spring 上下文一个实例 ID：同一个 JVM 里起两个实例（集成测试）也互不相同。
  @Test
  void should_give_each_call_its_own_instance_id() {
    assertThat(config.realtimeInstanceId()).isNotEqualTo(config.realtimeInstanceId());
  }

  /// 发布走单独的连接：RabbitMQ 资源告警时阻塞的是发过消息的连接，和监听共用就会连信箱消费一起停（spec 5.3）。
  @Test
  void should_publish_on_a_separate_connection() {
    RabbitTemplate template = mock(RabbitTemplate.class);

    config.publisherConnectionCustomizer().customize(template);

    verify(template).setUsePublisherConnection(true);
  }
}
