package dev.patra.notification.config;

import java.time.ZoneId;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/// 邮件渠道的配置：发件人在 Nacos `patra-notification.yaml`（线上由 `mail.env` 的 `PATRA_MAIL_FROM` 覆盖），
/// 显示时区在 jar 的 `application.yml`。SMTP 连接本身是 Boot 的 `spring.mail.*`。
///
/// @param from 发件人，形如 `Patra <no-reply@example.com>`
/// @param zone 邮件里时间的显示时区
@ConfigurationProperties("patra.notification.mail")
public record MailDeliveryProperties(String from, ZoneId zone) {

  /// 两项都必填：缺了启动失败，不等到第一封信才报错。
  public MailDeliveryProperties {
    Objects.requireNonNull(from, "patra.notification.mail.from 不能为空");
    Objects.requireNonNull(zone, "patra.notification.mail.zone 不能为空");
  }
}
