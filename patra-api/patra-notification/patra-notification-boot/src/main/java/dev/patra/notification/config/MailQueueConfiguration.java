package dev.patra.notification.config;

import dev.patra.identity.api.account.event.SecurityMailEvent;
import dev.patra.notification.adapter.mail.mq.SecurityMailListener;
import dev.patra.tool.events.consumer.EventQueue;
import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/// 邮件渠道的队列：安全邮件队列绑定 identity 的交换机，连同死信交换机与死信队列一起声明。
@Configuration(proxyBeanMethods = false)
public class MailQueueConfiguration {

  /// `notification.security-mail`：收 `identity.events` 上全部 `security-mail.*`；
  /// 死信对 `notification.security-mail.dlx` / `.dlq`。
  ///
  /// @return 队列、死信对、交换机与绑定
  @Bean
  public Declarables securityMailQueue() {
    return EventQueue.named(SecurityMailListener.QUEUE)
        .bind(SecurityMailEvent.EXCHANGE, SecurityMailEvent.ROUTING_KEY_PATTERN)
        .declarables();
  }
}
