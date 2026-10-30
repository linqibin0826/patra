package dev.patra.tool.events.publisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.patra.tool.core.event.IntegrationEvent;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.AmqpTimeoutException;
import org.springframework.amqp.core.AmqpMessageReturnedException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.connection.CorrelationData.Confirm;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;

@DisplayName("等 publisher confirm 的发送模板")
class ConfirmingRabbitMessagingTemplateTest {

  private static final Duration TIMEOUT = Duration.ofMillis(20);

  private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);

  private final ConnectionFactory connectionFactory = mock(ConnectionFactory.class);

  /// 测试用的集成事件。
  ///
  /// @param eventId 事件 ID
  /// @param name 名称
  record OrderPlaced(UUID eventId, String name) implements IntegrationEvent {}

  /// 测试结束清掉可能残留的中断标志。
  @AfterEach
  void clearInterrupt() {
    Thread.interrupted();
  }

  @Test
  @DisplayName("broker 回 ack 时正常返回")
  void should_return_when_broker_acks() {
    ConfirmingRabbitMessagingTemplate template =
        template(correlation -> correlation.getFuture().complete(new Confirm(true, null)));

    assertThatCode(() -> template.convertAndSend("ex", "rk", "hello")).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("集成事件的 eventId 用作 AMQP messageId；确认关联 ID 每次发送各不相同")
  void should_use_event_id_as_message_id_and_fresh_correlation_ids() {
    List<Message> sent = new CopyOnWriteArrayList<>();
    List<CorrelationData> correlations = new CopyOnWriteArrayList<>();
    stubSend(
        (message, correlation) -> {
          sent.add(message);
          correlations.add(correlation);
          correlation.getFuture().complete(new Confirm(true, null));
        });
    ConfirmingRabbitMessagingTemplate template = initialized(TIMEOUT);
    UUID eventId = UUID.randomUUID();
    OrderPlaced event = new OrderPlaced(eventId, "a");

    template.convertAndSend("ex", "rk", event);
    template.convertAndSend("ex", "rk", event);

    assertThat(sent)
        .extracting(message -> message.getMessageProperties().getMessageId())
        .containsOnly(eventId.toString());
    assertThat(correlations)
        .extracting(CorrelationData::getId)
        .doesNotHaveDuplicates()
        .doesNotContain(eventId.toString());
  }

  @Test
  @DisplayName("broker 回 nack 时抛异常并带上原因")
  void should_throw_when_broker_nacks() {
    ConfirmingRabbitMessagingTemplate template =
        template(
            correlation -> correlation.getFuture().complete(new Confirm(false, "reject-publish")));

    assertThatThrownBy(() -> template.convertAndSend("ex", "rk", "hello"))
        .isInstanceOf(AmqpException.class)
        .hasMessageContaining("reject-publish");
  }

  @Test
  @DisplayName("没有队列可投、消息被退回时抛异常")
  void should_throw_when_message_is_returned() {
    ConfirmingRabbitMessagingTemplate template =
        template(
            correlation -> {
              correlation.setReturned(
                  new ReturnedMessage(
                      new Message(new byte[0], new MessageProperties()),
                      312,
                      "NO_ROUTE",
                      "ex",
                      "rk"));
              correlation.getFuture().complete(new Confirm(true, null));
            });

    assertThatThrownBy(() -> template.convertAndSend("ex", "rk", "hello"))
        .isInstanceOf(AmqpMessageReturnedException.class);
  }

  @Test
  @DisplayName("等不到确认时超时抛异常")
  void should_throw_when_confirm_times_out() {
    ConfirmingRabbitMessagingTemplate template = template(correlation -> {});

    assertThatThrownBy(() -> template.convertAndSend("ex", "rk", "hello"))
        .isInstanceOf(AmqpTimeoutException.class);
  }

  @Test
  @DisplayName("等待时被中断，恢复中断标志后抛异常")
  void should_restore_interrupt_flag_when_interrupted() {
    ConfirmingRabbitMessagingTemplate template = template(correlation -> {});
    Thread.currentThread().interrupt();

    assertThatThrownBy(() -> template.convertAndSend("ex", "rk", "hello"))
        .isInstanceOf(AmqpException.class);
    assertThat(Thread.interrupted()).isTrue();
  }

  @Test
  @DisplayName("连接没开 publisher confirm 时初始化失败")
  void should_fail_fast_without_publisher_confirms() {
    stubConnectionFactory(false, true);
    ConfirmingRabbitMessagingTemplate template =
        new ConfirmingRabbitMessagingTemplate(rabbitTemplate, TIMEOUT);

    assertThatThrownBy(template::afterPropertiesSet)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("spring.rabbitmq.publisher-confirm-type=correlated");
  }

  @Test
  @DisplayName("连接没开退回时初始化失败")
  void should_fail_fast_without_publisher_returns() {
    stubConnectionFactory(true, false);
    ConfirmingRabbitMessagingTemplate template =
        new ConfirmingRabbitMessagingTemplate(rabbitTemplate, TIMEOUT);

    assertThatThrownBy(template::afterPropertiesSet)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("spring.rabbitmq.publisher-returns=true");
  }

  /// 建一个连接开了确认与退回、发送时按 `onSend` 处理关联数据的模板。
  ///
  /// @param onSend 发送时对关联数据的处理
  /// @return 已初始化的模板
  private ConfirmingRabbitMessagingTemplate template(Consumer<CorrelationData> onSend) {
    stubSend((message, correlation) -> onSend.accept(correlation));
    return initialized(TIMEOUT);
  }

  /// 建模板并走一遍初始化。
  ///
  /// @param timeout 等确认的上限
  /// @return 已初始化的模板
  private ConfirmingRabbitMessagingTemplate initialized(Duration timeout) {
    stubConnectionFactory(true, true);
    ConfirmingRabbitMessagingTemplate template =
        new ConfirmingRabbitMessagingTemplate(rabbitTemplate, timeout);
    template.afterPropertiesSet();
    return template;
  }

  /// 模拟连接工厂的确认与退回开关，以及模板的消息转换器。
  ///
  /// @param confirms 是否开了 publisher confirm
  /// @param returns 是否开了退回
  private void stubConnectionFactory(boolean confirms, boolean returns) {
    when(connectionFactory.isPublisherConfirms()).thenReturn(confirms);
    when(connectionFactory.isPublisherReturns()).thenReturn(returns);
    when(rabbitTemplate.getConnectionFactory()).thenReturn(connectionFactory);
    when(rabbitTemplate.getMessageConverter()).thenReturn(new JacksonJsonMessageConverter());
  }

  /// 拦下带关联数据的发送。
  ///
  /// @param handler 拿到 AMQP 消息与关联数据后的处理
  private void stubSend(SendHandler handler) {
    doAnswer(
            invocation -> {
              handler.handle(invocation.getArgument(2), invocation.getArgument(3));
              return null;
            })
        .when(rabbitTemplate)
        .send(eq("ex"), eq("rk"), any(Message.class), any(CorrelationData.class));
  }

  /// 发送拦截回调。
  @FunctionalInterface
  private interface SendHandler {

    /// 处理一次发送。
    ///
    /// @param message 发出的 AMQP 消息
    /// @param correlation 关联数据
    void handle(Message message, CorrelationData correlation);
  }
}
