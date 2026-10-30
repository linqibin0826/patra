package dev.patra.notification.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import dev.patra.identity.api.account.event.PasswordResetMailEvent;
import dev.patra.identity.api.account.event.SecurityMailEvent;
import dev.patra.notification.app.mail.usecase.security.SendSecurityMailCommand;
import dev.patra.notification.domain.mail.model.enums.SecurityMailKind;
import dev.patra.notification.infra.config.NotificationITPostgreSQLContainerInitializer;
import dev.patra.tool.cqrs.CommandBus;
import dev.patra.tool.test.container.initializer.RabbitMQContainerInitializer;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/// 安全邮件队列的监听：identity 发到 `identity.events` 的事件按类型头还原成具体 record，转成发信命令。
///
/// 监听方法的参数是 sealed 接口 `SecurityMailEvent`，这条测试证明转换器会退回按 `__TypeId__` 头反序列化
/// （spec 6.4）。命令总线是桩，只看收到的命令。
@SpringBootTest
@ContextConfiguration(
    initializers = {
      NotificationITPostgreSQLContainerInitializer.class,
      RabbitMQContainerInitializer.class
    })
@ActiveProfiles("test")
@DisplayName("安全邮件队列的监听")
class SecurityMailListenerIT {

  @Autowired private RabbitTemplate rabbitTemplate;

  @MockitoBean private CommandBus commandBus;

  /// 按 identity 外送的形状发一封找回密码事件，监听把它转成发信命令交给命令总线。
  @Test
  void should_dispatch_interface_typed_event_by_type_header() {
    UUID eventId = UUID.randomUUID();
    PasswordResetMailEvent event =
        PasswordResetMailEvent.builder()
            .eventId(eventId)
            .to("chen.yu@example.com")
            .displayName("陈宇")
            .resetUrl("https://patra.example/reset-password#token=rst")
            .linkExpiresAt(Instant.parse("2026-10-31T02:30:00Z"))
            .build();

    rabbitTemplate.convertAndSend(
        SecurityMailEvent.EXCHANGE, PasswordResetMailEvent.ROUTING_KEY, event);

    ArgumentCaptor<SendSecurityMailCommand> command =
        ArgumentCaptor.forClass(SendSecurityMailCommand.class);
    verify(commandBus, timeout(10_000)).handle(command.capture());
    assertThat(command.getValue().consumer()).isEqualTo("notification.security-mail");
    assertThat(command.getValue().mail().kind()).isEqualTo(SecurityMailKind.PASSWORD_RESET);
    assertThat(command.getValue().mail().eventId()).isEqualTo(eventId);
    assertThat(command.getValue().mail().facts())
        .containsEntry("resetUrl", "https://patra.example/reset-password#token=rst");
  }
}
