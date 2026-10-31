package dev.patra.tool.events.publisher;

import dev.patra.tool.core.event.IntegrationEvent;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.AmqpTimeoutException;
import org.springframework.amqp.core.AmqpMessageReturnedException;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.connection.CorrelationData.Confirm;
import org.springframework.amqp.rabbit.core.RabbitMessagingTemplate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/// 每次发送都同步等 RabbitMQ 的 publisher confirm 的 `RabbitMessagingTemplate`。
///
/// Modulith 的 AMQP 外送器调 `RabbitMessageOperations.convertAndSend` 后立即把登记行标成完成，不等 broker 回执；
/// 本类替换 Boot 的 `RabbitMessagingTemplate`，让发送调用在 broker 收下之前不返回：ack 且没被退回才算成功，
/// nack、退回（没有队列可投）、超时、等待被中断都抛异常，外送器据此把登记行留给补投。
///
/// 要求连接开了 `correlated` 确认与退回，`RabbitTemplate` 带 `mandatory`（由自动配置保证）。
public class ConfirmingRabbitMessagingTemplate extends RabbitMessagingTemplate {

  private final Duration confirmTimeout;

  /// 创建模板。
  ///
  /// @param rabbitTemplate 底层模板，连接须开确认与退回
  /// @param confirmTimeout 等确认的上限
  public ConfirmingRabbitMessagingTemplate(RabbitTemplate rabbitTemplate, Duration confirmTimeout) {
    super(rabbitTemplate);
    this.confirmTimeout = confirmTimeout;
  }

  /// 检查连接开了确认与退回，没开就初始化失败：否则发送不会有回执可等。
  @Override
  public void afterPropertiesSet() {
    super.afterPropertiesSet();
    ConnectionFactory connectionFactory = getRabbitTemplate().getConnectionFactory();
    if (!connectionFactory.isPublisherConfirms()) {
      throw new IllegalStateException(
          "事件外送要等 publisher confirm，连接没有开启：设置 spring.rabbitmq.publisher-confirm-type=correlated");
    }
    if (!connectionFactory.isPublisherReturns()) {
      throw new IllegalStateException(
          "事件外送要识别没有队列可投的消息，连接没有开启退回：设置 spring.rabbitmq.publisher-returns=true");
    }
  }

  /// 发到指定交换机，等 broker 确认后返回。
  ///
  /// @param exchange 交换机
  /// @param routingKey 路由键
  /// @param message 待发送的消息
  @Override
  protected void doSend(String exchange, String routingKey, Message<?> message) {
    assertNoTransactionalResources();
    CorrelationData correlation = new CorrelationData(UUID.randomUUID().toString());
    super.doSend(exchange, routingKey, withCorrelation(message, correlation));
    awaitConfirm(correlation);
  }

  /// 发到默认交换机（路由键即队列名），等 broker 确认后返回。
  ///
  /// @param destination 队列名
  /// @param message 待发送的消息
  @Override
  protected void doSend(String destination, Message<?> message) {
    assertNoTransactionalResources();
    CorrelationData correlation = new CorrelationData(UUID.randomUUID().toString());
    super.doSend(destination, withCorrelation(message, correlation));
    awaitConfirm(correlation);
  }

  /// 等确认会阻塞几秒到更久，期间线程上挂着的事务资源（数据库连接、EntityManager 等）一直不还，broker 一卡住
  /// 就能把连接池占光。Modulith 的外送器跑在 `SUPPORTS` 作用域里，本身不绑资源；外送路径上的选择、路由、映射、
  /// 消息转换只要查一次库，连接就会绑到线程上，这里直接拒绝，让问题在测试里暴露，而不是线上饿死连接池。
  private static void assertNoTransactionalResources() {
    if (!TransactionSynchronizationManager.getResourceMap().isEmpty()) {
      throw new IllegalStateException(
          "线程上挂着事务资源（数据库连接或 EntityManager）时不能等 broker 确认：外送路径（选择、路由、映射、"
              + "消息转换）不能查库，也不要在事务里经 RabbitMessageOperations 发送");
    }
  }

  /// 把关联数据放进消息头，父类据此带关联发送；集成事件还把 `eventId` 写成 AMQP `messageId`。
  ///
  /// 关联 ID 每次发送都是新的随机值，不用 `eventId`：信道按关联 ID 记录待退回的消息，同一事件的两次发送
  /// 并发时用同一个 ID 会互相覆盖，可能把一条被退回的消息当成发送成功。
  ///
  /// @param message 待发送的消息
  /// @param correlation 关联数据
  /// @return 带上消息头的新消息
  private static Message<?> withCorrelation(Message<?> message, CorrelationData correlation) {
    MessageBuilder<?> builder =
        MessageBuilder.fromMessage(message)
            .setHeader(AmqpHeaders.PUBLISH_CONFIRM_CORRELATION, correlation);
    if (message.getPayload() instanceof IntegrationEvent event) {
      builder.setHeader(AmqpHeaders.MESSAGE_ID, event.eventId().toString());
    }
    return builder.build();
  }

  /// 等确认：先看是否被退回（RabbitMQ 对退回的消息照样回 ack），再看 ack。
  ///
  /// @param correlation 关联数据
  private void awaitConfirm(CorrelationData correlation) {
    Confirm confirm;
    try {
      confirm = correlation.getFuture().get(confirmTimeout.toNanos(), TimeUnit.NANOSECONDS);
    } catch (TimeoutException ex) {
      throw new AmqpTimeoutException(
          "%s 内没有等到 broker 确认，关联 ID %s".formatted(confirmTimeout, correlation.getId()), ex);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new AmqpException("等待 broker 确认时被中断，关联 ID " + correlation.getId(), ex);
    } catch (ExecutionException ex) {
      throw new AmqpException("等待 broker 确认失败，关联 ID " + correlation.getId(), ex.getCause());
    }
    ReturnedMessage returned = correlation.getReturned();
    if (returned != null) {
      throw new AmqpMessageReturnedException(
          "没有队列可投，消息被退回：交换机 %s、路由键 %s、%s"
              .formatted(returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()),
          returned);
    }
    if (!confirm.isAck()) {
      throw new AmqpException(
          "broker 拒收（nack），关联 ID %s，原因：%s".formatted(correlation.getId(), confirm.getReason()));
    }
  }
}
