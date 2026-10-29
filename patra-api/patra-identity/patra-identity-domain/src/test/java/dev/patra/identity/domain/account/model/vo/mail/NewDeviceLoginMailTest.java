package dev.patra.identity.domain.account.model.vo.mail;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.session.model.vo.SecurityNotice;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// NewDeviceLoginMail 单元测试：模板名、主题、模板变量、不泄露内容（PAP-99 设计第 12.4 节）。
@DisplayName("NewDeviceLoginMail 单元测试")
class NewDeviceLoginMailTest {

  private static final EmailAddress TO = EmailAddress.of("chen.yu@example.com");
  private static final SecurityNotice NOTICE =
      SecurityNotice.builder()
          .occurredAt(Instant.parse("2026-10-22T08:00:00Z"))
          .notMeAdvice("x")
          .build();

  @Test
  @DisplayName("模板 new-device-login、主题固定、变量齐全")
  void should_expose_template_subject_and_model() {
    NewDeviceLoginMail mail = NewDeviceLoginMail.of(TO, "陈宇", NOTICE);

    assertThat(mail.template()).isEqualTo("new-device-login");
    assertThat(mail.subject()).isEqualTo("你的 Patra 账号在新设备上登录");
    assertThat(mail.model())
        .containsEntry("displayName", "陈宇")
        .containsEntry("notice", NOTICE)
        .hasSize(2);
    assertThat(mail.to()).isEqualTo(TO);
    assertThat(NewDeviceLoginMail.NOT_ME_ADVICE).contains("修改密码").contains("登录设备");
  }

  @Test
  @DisplayName("toString 不输出收件人")
  void should_not_leak_recipient_in_to_string() {
    assertThat(NewDeviceLoginMail.of(TO, "陈宇", NOTICE).toString()).doesNotContain("chen.yu");
  }
}
