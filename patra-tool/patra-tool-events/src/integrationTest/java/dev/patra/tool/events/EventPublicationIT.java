package dev.patra.tool.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import dev.patra.tool.events.resubmit.EventPublicationResubmitter;
import dev.patra.tool.events.support.EventsITAfterCommitListener;
import dev.patra.tool.events.support.EventsITFixture;
import dev.patra.tool.events.support.EventsITOrderListener;
import dev.patra.tool.events.support.EventsITOrderPlaced;
import dev.patra.tool.events.support.EventsITOrderPlacedMessage;
import dev.patra.tool.events.support.EventsITOrderService;
import dev.patra.tool.events.support.EventsITSpringConfig;
import dev.patra.tool.test.container.initializer.PostgreSQLContainerInitializer;
import dev.patra.tool.test.container.initializer.RabbitMQContainerInitializer;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitMessageOperations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

/// 事件模块端到端集成测试（PAP-137 验收）：真 PostgreSQL + 真 RabbitMQ。
///
/// 上下文用完即关（`@DirtiesContext`）：同一个 JVM 里另有确认超时用例的上下文，两套监听容器同时开着会抢订单队列的消息。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ContextConfiguration(
    initializers = {PostgreSQLContainerInitializer.class, RabbitMQContainerInitializer.class})
@DirtiesContext
@DisplayName("事件发布与消费集成测试")
class EventPublicationIT {

  /// Modulith AMQP 外送监听器的 ID，登记行按它找回监听器。
  private static final String EXTERNALIZER_ID =
      "org.springframework.modulith.events.support.EventExternalizerModuleListener.externalize(java.lang.Object)";

  private static final Duration WAIT = Duration.ofSeconds(10);

  @Autowired private EventsITOrderService orders;

  @Autowired private EventsITOrderListener listener;

  @Autowired private EventsITAfterCommitListener afterCommitListener;

  @Autowired private EventsITFixture fixture;

  @Autowired private EventPublicationResubmitter resubmitter;

  @Autowired private RabbitMessageOperations messageOperations;

  @Autowired private RabbitTemplate rabbitTemplate;

  @BeforeEach
  void reset() {
    fixture.reset();
  }

  @Test
  @DisplayName("事务提交后事件送达消费者，登记行发完即删")
  void should_deliver_event_after_commit() {
    orders.place(EventsITOrderPlaced.of("A-1"));

    await().atMost(WAIT).untilAsserted(() -> assertThat(fixture.effectCount()).isEqualTo(1));
    await().atMost(WAIT).untilAsserted(() -> assertThat(fixture.publicationStatuses()).isEmpty());
  }

  @Test
  @DisplayName("事务回滚时不登记、不外送")
  void should_neither_register_nor_send_when_transaction_rolls_back() {
    assertThatThrownBy(() -> orders.placeAndFail(EventsITOrderPlaced.of("A-2")))
        .isInstanceOf(IllegalStateException.class);

    assertThat(fixture.orderCount()).isZero();
    assertThat(fixture.publicationStatuses()).isEmpty();
    await()
        .during(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(3))
        .until(() -> listener.invocations() == 0);
  }

  @Test
  @Timeout(value = 120, unit = TimeUnit.SECONDS)
  @DisplayName("broker 不可达时业务照常提交、登记行未完成；恢复后补投送达，业务只执行一次")
  void should_keep_publication_incomplete_while_broker_is_down_and_deliver_once_after_resubmit() {
    fixture.stopBroker();
    try {
      orders.place(EventsITOrderPlaced.of("A-3"));

      assertThat(fixture.orderCount()).isEqualTo(1);
      await()
          .atMost(WAIT)
          .untilAsserted(() -> assertThat(fixture.publicationStatuses()).containsExactly("FAILED"));
      assertThat(fixture.publicationListenerIds()).containsExactly(EXTERNALIZER_ID);
    } finally {
      fixture.startBroker();
    }

    assertThat(resubmitter.resubmit()).isEqualTo(1);

    await().atMost(WAIT).untilAsserted(() -> assertThat(fixture.effectCount()).isEqualTo(1));
    await().atMost(WAIT).untilAsserted(() -> assertThat(fixture.publicationStatuses()).isEmpty());
    assertThat(listener.invocations()).isEqualTo(1);
  }

  @Test
  @DisplayName("普通的提交后监听器不进登记表：它失败也不留登记行，只有外送器的行")
  void should_not_register_plain_after_commit_listeners() {
    orders.place(EventsITOrderPlaced.of(EventsITAfterCommitListener.FAILING_PREFIX + "1"));

    await().atMost(WAIT).untilAsserted(() -> assertThat(fixture.effectCount()).isEqualTo(1));
    await()
        .atMost(WAIT)
        .untilAsserted(() -> assertThat(afterCommitListener.invocations()).isEqualTo(1));
    await().atMost(WAIT).untilAsserted(() -> assertThat(fixture.publicationStatuses()).isEmpty());
  }

  @Test
  @DisplayName("broker 回 nack 时登记行不被标为完成")
  void should_keep_publication_incomplete_when_broker_nacks() {
    orders.place(EventsITOrderPlaced.routedTo("A-4", EventsITSpringConfig.ORDER_REJECTED));

    await()
        .atMost(WAIT)
        .untilAsserted(() -> assertThat(fixture.publicationStatuses()).containsExactly("FAILED"));
  }

  @Test
  @DisplayName("没有队列可投、消息被退回时登记行不被标为完成")
  void should_keep_publication_incomplete_when_no_queue_is_bound() {
    orders.place(EventsITOrderPlaced.routedTo("A-5", EventsITSpringConfig.ORDER_NOWHERE));

    await()
        .atMost(WAIT)
        .untilAsserted(() -> assertThat(fixture.publicationStatuses()).containsExactly("FAILED"));
  }

  @Test
  @Timeout(value = 120, unit = TimeUnit.SECONDS)
  @DisplayName("补投途中崩溃、卡在 RESUBMITTED 的登记行，下一轮补投照样送达")
  void should_resubmit_publication_stuck_in_resubmitted() {
    fixture.stopBroker();
    try {
      orders.place(EventsITOrderPlaced.of("A-6"));
      await()
          .atMost(WAIT)
          .untilAsserted(() -> assertThat(fixture.publicationStatuses()).containsExactly("FAILED"));
    } finally {
      fixture.startBroker();
    }
    fixture.markStuckInResubmission();

    assertThat(resubmitter.resubmit()).isEqualTo(1);

    await().atMost(WAIT).untilAsserted(() -> assertThat(fixture.effectCount()).isEqualTo(1));
    await().atMost(WAIT).untilAsserted(() -> assertThat(fixture.publicationStatuses()).isEmpty());
  }

  @Test
  @DisplayName("同一事件投递两次，消费端业务只执行一次")
  void should_process_duplicate_delivery_only_once() {
    EventsITOrderPlacedMessage message =
        new EventsITOrderPlacedMessage(UUID.randomUUID(), "A-7", false);

    messageOperations.convertAndSend(
        EventsITSpringConfig.EXCHANGE, EventsITSpringConfig.ORDER_PLACED, message);
    messageOperations.convertAndSend(
        EventsITSpringConfig.EXCHANGE, EventsITSpringConfig.ORDER_PLACED, message);

    await().atMost(WAIT).untilAsserted(() -> assertThat(listener.invocations()).isEqualTo(2));
    assertThat(fixture.effectCount()).isEqualTo(1);
    assertThat(fixture.processedCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("处理失败超过重试次数的消息进入死信队列")
  void should_dead_letter_after_retries_are_exhausted() {
    orders.place(EventsITOrderPlaced.poison("A-8"));

    Message deadLetter = rabbitTemplate.receive(EventsITSpringConfig.ORDERS_DLQ, WAIT.toMillis());

    assertThat(deadLetter).isNotNull();
    assertThat(listener.invocations()).isEqualTo(4);
    assertThat(fixture.effectCount()).isZero();
    assertThat(fixture.processedCount()).isZero();
  }
}
