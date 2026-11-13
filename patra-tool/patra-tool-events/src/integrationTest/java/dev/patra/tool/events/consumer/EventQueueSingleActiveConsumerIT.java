package dev.patra.tool.events.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.patra.tool.test.container.initializer.RabbitMQContainerInitializer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.testcontainers.containers.RabbitMQContainer;

/// 单活跃消费者的真实行为：同一条队列挂两个消费者，消息只给先订阅的那个；它停了以后换另一个收。
///
/// 不起 Spring 上下文，直接用连接工厂和监听容器：要验证的是 `EventQueue` 声明出的队列参数让 RabbitMQ 这样投递，
/// 与服务的装配无关。
@DisplayName("单活跃消费者队列集成测试")
class EventQueueSingleActiveConsumerIT {

  private static final String QUEUE = "events.it.sac";
  private static final String EXCHANGE = "events.it.sac.x";
  private static final Duration WAIT = Duration.ofSeconds(10);

  private final List<String> firstReceived = new CopyOnWriteArrayList<>();
  private final List<String> secondReceived = new CopyOnWriteArrayList<>();

  private CachingConnectionFactory connectionFactory;
  private RabbitAdmin admin;
  private RabbitTemplate template;
  private SimpleMessageListenerContainer first;
  private SimpleMessageListenerContainer second;

  @BeforeEach
  void setUp() {
    RabbitMQContainer rabbitmq = RabbitMQContainerInitializer.getRabbitMQContainer();
    connectionFactory = new CachingConnectionFactory(rabbitmq.getHost(), rabbitmq.getAmqpPort());
    connectionFactory.setUsername(rabbitmq.getAdminUsername());
    connectionFactory.setPassword(rabbitmq.getAdminPassword());
    admin = new RabbitAdmin(connectionFactory);
    for (Declarable declarable :
        EventQueue.named(QUEUE)
            .bind(EXCHANGE, "k")
            .singleActiveConsumer()
            .declarables()
            .getDeclarables()) {
      switch (declarable) {
        case Queue queue -> admin.declareQueue(queue);
        case Exchange exchange -> admin.declareExchange(exchange);
        case Binding binding -> admin.declareBinding(binding);
        default -> throw new IllegalStateException("未知声明: " + declarable);
      }
    }
    template = new RabbitTemplate(connectionFactory);
    first = container(firstReceived);
    second = container(secondReceived);
  }

  @AfterEach
  void tearDown() {
    first.stop();
    second.stop();
    admin.deleteQueue(QUEUE);
    admin.deleteQueue(QUEUE + ".dlq");
    admin.deleteExchange(EXCHANGE);
    admin.deleteExchange(QUEUE + ".dlx");
    connectionFactory.destroy();
  }

  @Test
  @DisplayName("两个消费者只有先订阅的收到；它停了以后换另一个收")
  void should_deliver_to_first_consumer_only_then_fail_over() {
    first.start();
    second.start();
    send(0, 20);

    await().atMost(WAIT).untilAsserted(() -> assertThat(firstReceived).hasSize(20));
    assertThat(secondReceived).isEmpty();

    first.stop();
    send(20, 25);

    await().atMost(WAIT).untilAsserted(() -> assertThat(secondReceived).hasSize(5));
  }

  /// 发编号在 [from, to) 的消息。
  ///
  /// @param from 起始编号（含）
  /// @param to 结束编号（不含）
  private void send(int from, int to) {
    for (int i = from; i < to; i++) {
      template.convertAndSend(EXCHANGE, "k", "m" + i);
    }
  }

  /// 把收到的消息体记进给定列表的监听容器；`start()` 返回时消费者已经订阅。
  ///
  /// @param received 记录收到的消息
  /// @return 还没启动的容器
  private SimpleMessageListenerContainer container(List<String> received) {
    SimpleMessageListenerContainer container =
        new SimpleMessageListenerContainer(connectionFactory);
    container.setQueueNames(QUEUE);
    container.setMessageListener(
        (MessageListener)
            message -> received.add(new String(message.getBody(), StandardCharsets.UTF_8)));
    return container;
  }
}
