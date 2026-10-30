package dev.patra.notification.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.patra.identity.api.account.event.PasswordResetMailEvent;
import dev.patra.identity.api.account.event.SecurityMailEvent;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/// SMTP 一直不通：按退避重试 3 次后进死信队列，日志查得到事件 ID、查不到链接与收件地址（AC 3）。
///
/// 不挂 GreenMail：SMTP 指向一个刚释放的空闲端口，连上去就被拒绝。
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("安全邮件的死信")
class SecurityMailDeadLetterIT extends SecurityMailITSupport {

  /// SMTP 指向一个没人监听的端口。
  ///
  /// @param registry 动态属性
  /// @throws IOException 找不到空闲端口
  @DynamicPropertySource
  static void unreachableSmtp(DynamicPropertyRegistry registry) throws IOException {
    int port;
    try (ServerSocket socket = new ServerSocket(0)) {
      port = socket.getLocalPort();
    }
    int closed = port;
    registry.add("spring.mail.host", () -> "127.0.0.1");
    registry.add("spring.mail.port", () -> closed);
  }

  /// 重试耗尽进死信：死信里的 messageId 就是事件 ID，没有发送记录；日志有「Retries exhausted」与事件 ID，
  /// 没有链接与收件地址。
  @Test
  void should_dead_letter_after_retries(CapturedOutput output) {
    PasswordResetMailEvent event =
        passwordReset("dead-letter@example.com", Instant.parse("2099-01-01T00:00:00Z"));

    publish(event, PasswordResetMailEvent.ROUTING_KEY);

    Message dead = rabbitTemplate.receive(DEAD_LETTER_QUEUE, 15_000);
    assertThat(dead).isNotNull();
    assertThat(dead.getMessageProperties().getMessageId()).isEqualTo(event.eventId().toString());
    assertThat(deliveryRows(event.eventId())).isZero();
    await()
        .atMost(Duration.ofSeconds(5))
        .until(() -> output.getAll().contains("Retries exhausted"));
    assertThat(output.getAll())
        .contains(event.eventId().toString())
        .contains("安全邮件发送失败")
        .doesNotContain("dead-letter@example.com")
        .doesNotContain(event.resetUrl());
  }

  /// 类型头不在信任包里的消息反序列化不了：重试耗尽后进死信，不拖垮监听。
  @Test
  void should_dead_letter_untrusted_type() {
    MessageProperties properties = new MessageProperties();
    properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
    properties.setHeader("__TypeId__", "dev.patra.evil.Payload");
    rabbitTemplate.send(
        SecurityMailEvent.EXCHANGE,
        PasswordResetMailEvent.ROUTING_KEY,
        MessageBuilder.withBody("{}".getBytes(StandardCharsets.UTF_8))
            .andProperties(properties)
            .build());

    Message dead = rabbitTemplate.receive(DEAD_LETTER_QUEUE, 15_000);
    assertThat(dead).isNotNull();
    assertThat(dead.getMessageProperties().getHeaders().get("__TypeId__"))
        .isEqualTo("dev.patra.evil.Payload");
  }
}
