package dev.patra.notification.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.patra.notification.domain.mail.policy.ReminderLinks;
import dev.patra.notification.domain.mail.policy.ReminderMailComposer;
import dev.patra.notification.domain.mail.policy.ReminderTimeText;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;

/// ReminderMailConfiguration 单元测试：门户地址来自 `patra.notification.mail.links.base-url`，时区复用邮件渠道的配置；
/// 地址缺失或不是绝对的 http(s) 地址时启动失败（spec 第 10 节）。
@DisplayName("ReminderMailConfiguration 单元测试")
class ReminderMailConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(MailDeliveryConfiguration.class, ReminderMailConfiguration.class)
          .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
          .withPropertyValues(
              "patra.notification.mail.from=Patra <no-reply@patra.local>",
              "patra.notification.mail.zone=Asia/Shanghai");

  /// 配齐时装配出链接、时间写法与拼装规则；末尾的 `/` 去掉。
  @Test
  void should_create_composer_with_portal_base_and_zone() {
    runner
        .withPropertyValues("patra.notification.mail.links.base-url=https://patra.linqibin.dev/")
        .run(
            context -> {
              assertThat(context).hasSingleBean(ReminderMailComposer.class);
              assertThat(context.getBean(ReminderLinks.class).baseUrl())
                  .isEqualTo("https://patra.linqibin.dev");
              assertThat(context.getBean(ReminderTimeText.class).zone())
                  .isEqualTo(ZoneId.of("Asia/Shanghai"));
            });
  }

  /// 没配门户地址：启动失败。
  @Test
  void should_fail_fast_without_base_url() {
    runner.run(context -> assertThat(context).hasFailed());
  }

  /// 门户地址不是绝对的 http(s) 地址：启动失败。
  @ParameterizedTest
  @ValueSource(strings = {"patra.linqibin.dev", "/unsubscribe", "ftp://patra.linqibin.dev", " "})
  void should_fail_fast_on_invalid_base_url(String baseUrl) {
    runner
        .withPropertyValues("patra.notification.mail.links.base-url=" + baseUrl)
        .run(context -> assertThat(context).hasFailed());
  }
}
