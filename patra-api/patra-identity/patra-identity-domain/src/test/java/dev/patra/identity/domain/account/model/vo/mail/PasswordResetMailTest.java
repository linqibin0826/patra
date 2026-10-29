package dev.patra.identity.domain.account.model.vo.mail;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.session.model.vo.SecurityNotice;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// PasswordResetMail 单元测试：模板名、主题、模板变量、不泄露内容。
@DisplayName("PasswordResetMail 单元测试")
class PasswordResetMailTest {

  private static final EmailAddress TO = EmailAddress.of("chen.yu@example.com");
  private static final SecurityNotice NOTICE =
      SecurityNotice.builder()
          .occurredAt(Instant.parse("2026-10-22T08:00:00Z"))
          .notMeAdvice("x")
          .build();

  @Test
  @DisplayName("模板 password-reset、主题固定、变量齐全")
  void should_expose_template_subject_and_model() {
    PasswordResetMail mail = PasswordResetMail.of(TO, "陈宇", "https://p/reset-password#token=t");

    assertThat(mail.template()).isEqualTo("password-reset");
    assertThat(mail.subject()).isEqualTo("重设你的 Patra 密码");
    assertThat(mail.model())
        .containsEntry("displayName", "陈宇")
        .containsEntry("resetUrl", "https://p/reset-password#token=t")
        .containsEntry("validMinutes", 60L)
        .hasSize(3);
    assertThat(mail.to()).isEqualTo(TO);
  }

  @Test
  @DisplayName("toString 不输出收件人与链接")
  void should_not_leak_recipient_or_links_in_to_string() {
    PasswordResetMail mail = PasswordResetMail.of(TO, "陈宇", "https://p/reset-password#token=t");

    assertThat(mail.toString()).doesNotContain("chen.yu").doesNotContain("token=");
  }
}
