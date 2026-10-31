package dev.patra.notification.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.patra.notification.domain.mail.port.delivery.MailDeliveryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;

/// MailDeliveryConfiguration 单元测试：发件人与显示时区来自 `patra.notification.mail.*`，不合法时启动失败。
@DisplayName("MailDeliveryConfiguration 单元测试")
class MailDeliveryConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(MailDeliveryConfiguration.class)
          .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class));

  /// 配齐发件人与时区时装配出发信端口。
  @Test
  void should_create_mail_delivery_port() {
    runner
        .withPropertyValues(
            "patra.notification.mail.from=Patra <no-reply@patra.local>",
            "patra.notification.mail.zone=Asia/Shanghai")
        .run(context -> assertThat(context).hasSingleBean(MailDeliveryPort.class));
  }

  /// 发件人不是恰好一个合法地址：启动失败。
  @Test
  void should_fail_fast_on_invalid_from() {
    runner
        .withPropertyValues(
            "patra.notification.mail.from=a@example.com, b@example.com",
            "patra.notification.mail.zone=Asia/Shanghai")
        .run(context -> assertThat(context).hasFailed());
  }

  /// 没配发件人：启动失败，不等到第一封信才报错。
  @Test
  void should_fail_fast_without_from() {
    runner
        .withPropertyValues("patra.notification.mail.zone=Asia/Shanghai")
        .run(context -> assertThat(context).hasFailed());
  }
}
