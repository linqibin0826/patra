package dev.patra.notification.config;

import dev.patra.notification.domain.mail.port.delivery.MailDeliveryPort;
import dev.patra.notification.infra.mail.delivery.SmtpMailDeliveryAdapter;
import dev.patra.notification.infra.mail.delivery.ThymeleafMailRenderer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/// 邮件渠道的装配：模板渲染器与 SMTP 发信端口。发件人不是恰好一个合法地址、缺配置时启动失败。
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MailDeliveryProperties.class)
public class MailDeliveryConfiguration {

  /// 模板渲染器，时间按配置的时区显示。
  ///
  /// @param properties 邮件配置
  /// @return 渲染器
  @Bean
  public ThymeleafMailRenderer mailRenderer(MailDeliveryProperties properties) {
    return new ThymeleafMailRenderer(properties.zone());
  }

  /// 发信端口：SMTP 同步发出，每封带 Resend 幂等键。
  ///
  /// @param mailSender Boot 按 `spring.mail.*` 装配的发信器
  /// @param renderer 渲染器
  /// @param properties 邮件配置
  /// @return 端口
  @Bean
  public MailDeliveryPort mailDeliveryPort(
      JavaMailSender mailSender,
      ThymeleafMailRenderer renderer,
      MailDeliveryProperties properties) {
    return new SmtpMailDeliveryAdapter(mailSender, renderer, properties.from());
  }
}
