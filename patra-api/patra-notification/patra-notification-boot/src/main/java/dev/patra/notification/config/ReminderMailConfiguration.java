package dev.patra.notification.config;

import dev.patra.notification.domain.mail.policy.ReminderLinks;
import dev.patra.notification.domain.mail.policy.ReminderMailComposer;
import dev.patra.notification.domain.mail.policy.ReminderTimeText;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/// 提醒邮件的装配（PAP-146）：门户链接、时间写法（时区复用邮件渠道的 `patra.notification.mail.zone`）与拼装规则。
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PortalLinkProperties.class)
public class ReminderMailConfiguration {

  /// 门户链接。
  ///
  /// @param properties 门户地址
  /// @return 链接
  @Bean
  public ReminderLinks reminderLinks(PortalLinkProperties properties) {
    return ReminderLinks.of(properties.baseUrl().strip());
  }

  /// 邮件里的时间写法（北京时间）。
  ///
  /// @param properties 邮件渠道配置
  /// @return 时间写法
  @Bean
  public ReminderTimeText reminderTimeText(MailDeliveryProperties properties) {
    return ReminderTimeText.of(properties.zone());
  }

  /// 提醒邮件的拼装规则。
  ///
  /// @param links 门户链接
  /// @param time 时间写法
  /// @return 拼装规则
  @Bean
  public ReminderMailComposer reminderMailComposer(ReminderLinks links, ReminderTimeText time) {
    return new ReminderMailComposer(links, time);
  }
}
