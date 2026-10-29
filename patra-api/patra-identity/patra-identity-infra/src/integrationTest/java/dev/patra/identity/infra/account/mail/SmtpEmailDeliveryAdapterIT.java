package dev.patra.identity.infra.account.mail;

import static org.assertj.core.api.Assertions.assertThat;

import com.icegreen.greenmail.util.GreenMail;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.mail.EmailVerificationMail;
import dev.patra.identity.domain.account.policy.MailLinkPolicy;
import dev.patra.tool.test.mail.GreenMailSmtpInitializer;
import jakarta.mail.BodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/// SmtpEmailDeliveryAdapter 集成测试：真实的 `JavaMailSenderImpl` + 异步执行器，发到 JVM 内的 GreenMail。
@DisplayName("SmtpEmailDeliveryAdapter 集成测试")
class SmtpEmailDeliveryAdapterIT {

  private static final String VERIFY_URL = "https://patra.example/verify-email#token=abc";

  private final GreenMail greenMail = GreenMailSmtpInitializer.getGreenMail();

  @BeforeEach
  void purge() {
    GreenMailSmtpInitializer.purge();
  }

  @Test
  @DisplayName("异步发出一封验证邮件：GreenMail 收到，主题解码正确，两部分都含链接，带自动邮件头")
  void should_deliver_asynchronously_over_smtp() throws Exception {
    JavaMailSenderImpl sender = new JavaMailSenderImpl();
    sender.setHost("127.0.0.1");
    sender.setPort(GreenMailSmtpInitializer.getSmtpPort());
    ThymeleafMailRenderer renderer =
        new ThymeleafMailRenderer(
            MailLinkPolicy.builder()
                .baseUrl("https://patra.example")
                .verifyEmailPath("/verify-email")
                .securitySettingsPath("/settings/security")
                .resetPasswordPath("/reset-password")
                .confirmEmailChangePath("/confirm-email-change")
                .revertEmailChangePath("/revert-email-change")
                .build(),
            ZoneId.of("Asia/Shanghai"));
    try (SmtpEmailDeliveryAdapter adapter =
        SmtpEmailDeliveryAdapter.async(sender, renderer, "Patra <no-reply@patra.local>", 2)) {

      adapter.send(
          EmailVerificationMail.of(
              EmailAddress.of("chen.yu@example.com"),
              "陈宇",
              VERIFY_URL,
              Instant.parse("2026-10-22T08:00:00Z")));

      assertThat(greenMail.waitForIncomingEmail(5_000, 1)).isTrue();
    }
    MimeMessage received = greenMail.getReceivedMessages()[0];
    assertThat(received.getSubject()).isEqualTo("验证你的 Patra 邮箱");
    assertThat(received.getFrom()[0].toString()).isEqualTo("Patra <no-reply@patra.local>");
    assertThat(received.getAllRecipients()[0].toString()).isEqualTo("chen.yu@example.com");
    assertThat(received.getHeader("Auto-Submitted")).containsExactly("auto-generated");
    assertThat(received.getHeader("X-Auto-Response-Suppress")).containsExactly("All");
    MimeMultipart alternative = findAlternative(received.getContent());
    assertThat(alternative).isNotNull();
    assertThat(alternative.getCount()).isEqualTo(2);
    BodyPart text = alternative.getBodyPart(0);
    BodyPart html = alternative.getBodyPart(1);
    assertThat(text.getContentType()).startsWith("text/plain");
    assertThat(html.getContentType()).startsWith("text/html");
    assertThat(text.getContent().toString()).contains(VERIFY_URL).contains("陈宇");
    assertThat(html.getContent().toString()).contains(VERIFY_URL).contains("陈宇");
  }

  /// 递归找出 `multipart/alternative` 部分。
  ///
  /// @param content 邮件或某一部分的内容
  /// @return alternative 部分；没有时为 `null`
  private static MimeMultipart findAlternative(Object content) throws Exception {
    if (content instanceof MimeMultipart multipart) {
      if (multipart.getContentType().startsWith("multipart/alternative")) {
        return multipart;
      }
      for (int i = 0; i < multipart.getCount(); i++) {
        MimeMultipart found = findAlternative(multipart.getBodyPart(i).getContent());
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }
}
