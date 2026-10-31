package dev.patra.notification.mail;

import static org.awaitility.Awaitility.await;

import dev.patra.identity.api.account.event.PasswordResetMailEvent;
import dev.patra.identity.api.account.event.SecurityMailEvent;
import dev.patra.notification.adapter.mail.mq.SecurityMailListener;
import dev.patra.notification.infra.config.NotificationITPostgreSQLContainerInitializer;
import dev.patra.tool.test.container.initializer.RabbitMQContainerInitializer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

/// 安全邮件端到端测试的共用基座：真实的 PostgreSQL 与 RabbitMQ，消息按 identity 外送的形状发到 `identity.events`。
///
/// 每个用例前清空两张表与队列；本地重试间隔压到 100 毫秒（次数仍是事件模块的 3 次），死信测试不用等 7 秒。
/// 收信的子类再挂 GreenMail 初始化器。
///
/// 测试 profile 默认不起 RabbitMQ 监听（`application-test.yml`：没起 RabbitMQ 容器的上下文别去连），起了容器的
/// 测试要自己打开，同 `NotificationFlowITSupport`；不打开时安全邮件队列没人消费，用例全部超时。
@SpringBootTest
@ContextConfiguration(
    initializers = {
      NotificationITPostgreSQLContainerInitializer.class,
      RabbitMQContainerInitializer.class
    })
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "spring.rabbitmq.listener.simple.auto-startup=true",
      "spring.rabbitmq.listener.simple.retry.initial-interval=100ms",
      "spring.rabbitmq.listener.simple.retry.max-interval=400ms"
    })
abstract class SecurityMailITSupport {

  /// 死信队列名。
  static final String DEAD_LETTER_QUEUE = SecurityMailListener.QUEUE + ".dlq";

  @Autowired RabbitTemplate rabbitTemplate;
  @Autowired AmqpAdmin amqpAdmin;
  @Autowired JdbcClient jdbcClient;

  /// 清空发送记录、已处理事件与两个队列。
  @BeforeEach
  void resetState() {
    jdbcClient.sql("DELETE FROM mail.mail_delivery").update();
    jdbcClient.sql("DELETE FROM processed_event").update();
    amqpAdmin.purgeQueue(SecurityMailListener.QUEUE, false);
    amqpAdmin.purgeQueue(DEAD_LETTER_QUEUE, false);
  }

  /// 照 identity 外送的形状发一个事件：JSON 正文、类型头是 record 的全类名、路由键 `security-mail.<模板名>`、
  /// AMQP `messageId` 是事件 ID（事件模块外送时这样写）。
  ///
  /// @param event 事件
  /// @param routingKey 路由键
  void publish(SecurityMailEvent event, String routingKey) {
    rabbitTemplate.convertAndSend(
        SecurityMailEvent.EXCHANGE,
        routingKey,
        event,
        message -> {
          message.getMessageProperties().setMessageId(event.eventId().toString());
          return message;
        });
  }

  /// 等到这个投递 ID 有了发送记录。
  ///
  /// @param deliveryId 投递 ID
  /// @return 记录的一行
  Map<String, Object> awaitDelivery(UUID deliveryId) {
    await().atMost(Duration.ofSeconds(15)).until(() -> deliveryRows(deliveryId) == 1);
    return jdbcClient
        .sql("SELECT * FROM mail.mail_delivery WHERE delivery_id = ?")
        .param(deliveryId)
        .query()
        .singleRow();
  }

  /// 这个投递 ID 的发送记录行数。
  ///
  /// @param deliveryId 投递 ID
  /// @return 行数
  int deliveryRows(UUID deliveryId) {
    return jdbcClient
        .sql("SELECT count(*) FROM mail.mail_delivery WHERE delivery_id = ?")
        .param(deliveryId)
        .query(Integer.class)
        .single();
  }

  /// 这个事件在安全邮件队列的「已处理」行数。
  ///
  /// @param eventId 事件 ID
  /// @return 行数
  int processedRows(UUID eventId) {
    return jdbcClient
        .sql("SELECT count(*) FROM processed_event WHERE consumer = ? AND event_id = ?")
        .param(SecurityMailListener.QUEUE)
        .param(eventId)
        .query(Integer.class)
        .single();
  }

  /// 发到 `to`、链接在 `expiresAt` 过期的找回密码事件。
  ///
  /// @param to 收件地址
  /// @param expiresAt 链接过期时刻
  /// @return 事件
  static PasswordResetMailEvent passwordReset(String to, Instant expiresAt) {
    return PasswordResetMailEvent.builder()
        .eventId(UUID.randomUUID())
        .to(to)
        .displayName("陈宇")
        .resetUrl("https://patra.example/reset-password#token=" + UUID.randomUUID())
        .linkExpiresAt(expiresAt)
        .build();
  }

  /// 这些投递 ID 的发送记录总行数。
  ///
  /// @param deliveryIds 投递 ID
  /// @return 行数
  int deliveryRows(List<UUID> deliveryIds) {
    return deliveryIds.stream().mapToInt(this::deliveryRows).sum();
  }
}
