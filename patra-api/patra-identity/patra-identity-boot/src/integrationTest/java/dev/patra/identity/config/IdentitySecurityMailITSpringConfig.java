package dev.patra.identity.config;

import dev.patra.identity.api.account.event.SecurityMailEvent;
import dev.patra.tool.events.consumer.EventQueue;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/// 集成测试里代替通知服务收安全邮件事件（PAP-104）：一个绑定 `identity.events` / `security-mail.*` 的捕获队列。
///
/// 事件模块只把有队列可投的事件算发出（`mandatory`）：没有这个绑定，外送会被 broker 退回、登记行留在各上下文共用的库里，
/// 拖住别的测试，所以每个起应用的测试都要导入本配置。
@TestConfiguration(proxyBeanMethods = false)
public class IdentitySecurityMailITSpringConfig {

  /// 捕获队列。
  public static final String QUEUE = "identity-it.security-mail";

  /// 捕获队列与它的死信对，绑到 identity 的交换机上收全部安全邮件事件。
  ///
  /// @return 队列声明
  @Bean
  public Declarables identityItSecurityMailQueue() {
    return EventQueue.named(QUEUE)
        .bind(SecurityMailEvent.EXCHANGE, SecurityMailEvent.ROUTING_KEY_PATTERN)
        .declarables();
  }

  /// 读捕获队列、查登记表的测试夹具。
  ///
  /// @param rabbitTemplate 收消息
  /// @param amqpAdmin 清队列
  /// @param jdbcTemplate 查登记表
  /// @return 夹具
  @Bean
  public IdentitySecurityMailITFixture identitySecurityMailITFixture(
      RabbitTemplate rabbitTemplate, AmqpAdmin amqpAdmin, JdbcTemplate jdbcTemplate) {
    return new IdentitySecurityMailITFixture(rabbitTemplate, amqpAdmin, jdbcTemplate);
  }
}
