package dev.patra.identity.domain.account.model.vo.mail;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.session.model.vo.SecurityNotice;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// EmailChangeRevertedMail 单元测试：模板名、主题、模板变量、不泄露内容。
@DisplayName("EmailChangeRevertedMail 单元测试")
class EmailChangeRevertedMailTest {

  private static final EmailAddress TO = EmailAddress.of("chen.yu@example.com");
  private static final SecurityNotice NOTICE =
      SecurityNotice.builder()
          .occurredAt(Instant.parse("2026-10-22T08:00:00Z"))
          .notMeAdvice("x")
          .build();

  @Test
  @DisplayName("模板 email-change-reverted、主题固定、变量齐全")
  void should_expose_template_subject_and_model() {
    EmailChangeRevertedMail mail =
        EmailChangeRevertedMail.of(TO, "陈宇", "https://p/reset-password#token=t", NOTICE);

    assertThat(mail.template()).isEqualTo("email-change-reverted");
    assertThat(mail.subject()).isEqualTo("你的 Patra 邮箱已改回，请重设密码");
    assertThat(mail.model())
        .containsEntry("displayName", "陈宇")
        .containsEntry("resetUrl", "https://p/reset-password#token=t")
        .containsEntry("validMinutes", 60L)
        .containsEntry("notice", NOTICE)
        .hasSize(4);
    assertThat(mail.to()).isEqualTo(TO);
  }

  @Test
  @DisplayName("toString 不输出收件人与链接")
  void should_not_leak_recipient_or_links_in_to_string() {
    EmailChangeRevertedMail mail =
        EmailChangeRevertedMail.of(TO, "陈宇", "https://p/reset-password#token=t", NOTICE);

    assertThat(mail.toString()).doesNotContain("chen.yu").doesNotContain("token=");
  }
}
