package dev.patra.identity.domain.account.model.vo.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.identity.domain.account.model.vo.EmailAddress;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// EmailVerificationMail 单元测试：模板名、主题、模板变量、不泄露内容。
@DisplayName("EmailVerificationMail 单元测试")
class EmailVerificationMailTest {

  @Test
  @DisplayName("模板 email-verification、主题固定、变量是昵称、链接、有效小时数")
  void should_expose_template_subject_and_model() {
    EmailVerificationMail mail =
        EmailVerificationMail.of(
            EmailAddress.of("chen.yu@example.com"),
            "陈宇",
            "http://localhost:4000/verify-email#token=x",
            Instant.parse("2026-10-22T08:00:00Z"));

    assertThat(mail.template()).isEqualTo("email-verification");
    assertThat(mail.subject()).isEqualTo("验证你的 Patra 邮箱");
    assertThat(mail.model())
        .containsEntry("displayName", "陈宇")
        .containsEntry("verifyUrl", "http://localhost:4000/verify-email#token=x")
        .containsEntry("validHours", 24L)
        .hasSize(3);
    assertThat(mail.to().value()).isEqualTo("chen.yu@example.com");
    assertThat(mail.toString()).doesNotContain("chen.yu").doesNotContain("token=");
  }

  @Test
  @DisplayName("收件人、昵称、链接、过期时间都不能为 null")
  void should_reject_null_fields() {
    EmailAddress to = EmailAddress.of("a@example.com");
    assertThatThrownBy(() -> EmailVerificationMail.of(null, "a", "u", Instant.EPOCH))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> EmailVerificationMail.of(to, null, "u", Instant.EPOCH))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> EmailVerificationMail.of(to, "a", null, Instant.EPOCH))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> EmailVerificationMail.of(to, "a", "u", null))
        .isInstanceOf(NullPointerException.class);
  }
}
