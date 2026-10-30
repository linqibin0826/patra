package dev.patra.tool.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.patra.tool.events.resubmit.EventPublicationResubmitter;
import dev.patra.tool.events.support.EventsITFixture;
import dev.patra.tool.events.support.EventsITOrderListener;
import dev.patra.tool.events.support.EventsITOrderPlaced;
import dev.patra.tool.events.support.EventsITOrderService;
import dev.patra.tool.test.container.initializer.PostgreSQLContainerInitializer;
import dev.patra.tool.test.container.initializer.RabbitMQContainerInitializer;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

/// 等不到确认的集成测试：broker 的 ack 送不到发送模板等着的那个关联数据，模板必然等满确认超时。
///
/// 不靠「超时设得极短、ack 来不及」的时间赛跑（ack 偶尔先到就会被当成成功）：本类的测试配置给 `RabbitTemplate`
/// 设一个关联数据后处理器，发送时把登记到信道上的关联数据换成新对象，ack 只完成新对象，模板等的 future 永远不完成。
/// 消息其实已经进了队列，所以同时验证「至少一次 + 去重」：登记行留着、补投再发一遍，消费端业务仍只执行一次。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ContextConfiguration(
    initializers = {PostgreSQLContainerInitializer.class, RabbitMQContainerInitializer.class})
@TestPropertySource(properties = "patra.events.publisher.confirm-timeout=200ms")
@DirtiesContext
@DisplayName("确认超时集成测试")
class EventPublicationConfirmTimeoutIT {

  private static final Duration WAIT = Duration.ofSeconds(10);

  /// 让 ack 落不到模板等着的关联数据上。
  @TestConfiguration(proxyBeanMethods = false)
  static class UnreachableConfirmConfig {

    /// 发送时把登记到信道上的关联数据换成同 ID 的新对象：ack 完成的是新对象，模板等的原对象一直不完成。
    ///
    /// @return 模板定制
    @Bean
    RabbitTemplateCustomizer unreachableConfirmCustomizer() {
      return template ->
          template.setCorrelationDataPostProcessor(
              (message, correlation) ->
                  correlation == null ? null : new CorrelationData(correlation.getId()));
    }
  }

  @Autowired private EventsITOrderService orders;

  @Autowired private EventsITOrderListener listener;

  @Autowired private EventsITFixture fixture;

  @Autowired private EventPublicationResubmitter resubmitter;

  @BeforeEach
  void reset() {
    fixture.reset();
  }

  @Test
  @DisplayName("等不到确认时登记行不被标为完成；补投再发一遍，messageId 不变，业务只执行一次")
  void should_keep_publication_incomplete_when_confirm_times_out_and_dedup_resubmission() {
    EventsITOrderPlaced event = EventsITOrderPlaced.of("T-1");

    orders.place(event);

    await()
        .atMost(WAIT)
        .untilAsserted(
            () -> {
              assertThat(fixture.publicationStatuses()).containsExactly("FAILED");
              assertThat(fixture.effectCount()).isEqualTo(1);
            });

    resubmitter.resubmit();

    await().atMost(WAIT).untilAsserted(() -> assertThat(listener.invocations()).isEqualTo(2));
    assertThat(listener.messageIds()).containsOnly(event.eventId().toString());
    assertThat(fixture.effectCount()).isEqualTo(1);
    await()
        .atMost(WAIT)
        .untilAsserted(() -> assertThat(fixture.publicationStatuses()).containsExactly("FAILED"));
  }
}
