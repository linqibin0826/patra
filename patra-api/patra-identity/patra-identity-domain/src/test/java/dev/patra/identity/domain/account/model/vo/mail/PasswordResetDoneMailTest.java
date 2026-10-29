package dev.patra.identity.domain.account.model.vo.mail;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.session.model.vo.SecurityNotice;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// PasswordResetDoneMail 单元测试：模板名、主题、模板变量、不泄露内容。
@DisplayName("PasswordResetDoneMail 单元测试")
class PasswordResetDoneMailTest {

  private static final EmailAddress TO = EmailAddress.of("chen.yu@example.com");
  private static final SecurityNotice NOTICE =
      SecurityNotice.builder()
          .occurredAt(Instant.parse("2026-10-22T08:00:00Z"))
          .notMeAdvice("x")
          .build();

  @Test
  @DisplayName("模板 password-reset-done、主题固定、变量齐全")
  void should_expose_template_subject_and_model() {
    PasswordResetDoneMail mail = PasswordResetDoneMail.of(TO, "陈宇", NOTICE);

    assertThat(mail.template()).isEqualTo("password-reset-done");
    assertThat(mail.subject()).isEqualTo("你的 Patra 密码已重设");
    assertThat(mail.model())
        .containsEntry("displayName", "陈宇")
        .containsEntry("notice", NOTICE)
        .hasSize(2);
    assertThat(mail.to()).isEqualTo(TO);
  }

  @Test
  @DisplayName("toString 不输出收件人与链接")
  void should_not_leak_recipient_or_links_in_to_string() {
    PasswordResetDoneMail mail = PasswordResetDoneMail.of(TO, "陈宇", NOTICE);

    assertThat(mail.toString()).doesNotContain("chen.yu");
  }
}
