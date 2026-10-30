package dev.patra.notification.infra.mail.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.notification.domain.mail.exception.MailDeliveryFailedException;
import dev.patra.notification.domain.mail.model.enums.SecurityMailKind;
import dev.patra.notification.domain.mail.model.vo.OutgoingMail;
import dev.patra.notification.domain.mail.model.vo.SecurityMail;
import jakarta.mail.BodyPart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

/// SmtpMailDeliveryAdapter 单元测试：MIME 结构、固定的头与幂等键、失败转成脱敏的领域异常、发件人校验。
@DisplayName("SmtpMailDeliveryAdapter 单元测试")
class SmtpMailDeliveryAdapterTest {

  private static final String FROM = "Patra <no-reply@patra.local>";
  private static final String TO = "chen.yu@example.com";
  private static final String RESET_URL = "https://patra.example/reset-password#token=rst";
  private static final UUID EVENT_ID = UUID.fromString("3c9d8e7f-6a5b-4c3d-9e2f-0a1b2c3d4e5f");

  private final JavaMailSender mailSender = mock(JavaMailSender.class);
  private final ThymeleafMailRenderer renderer =
      new ThymeleafMailRenderer(ZoneId.of("Asia/Shanghai"));
  private final OutgoingMail mail =
      SecurityMail.builder()
          .eventId(EVENT_ID)
          .kind(SecurityMailKind.PASSWORD_RESET)
          .to(TO)
          .displayName("陈宇")
          .facts(Map.of("resetUrl", RESET_URL))
          .linkExpiresAt(Instant.parse("2026-10-31T02:30:00Z"))
          .build()
          .compose();

  @Test
  @DisplayName("发出一封 multipart/alternative：发件人、收件人、主题、固定的三个头（含幂等键）、文本在前 HTML 在后，没有退订头")
  void should_send_multipart_alternative_with_fixed_headers() throws Exception {
    MimeMessage message = deliverAndCapture().getFirst();

    assertThat(message.getFrom()[0].toString()).isEqualTo(FROM);
    assertThat(message.getAllRecipients()).hasSize(1);
    assertThat(message.getAllRecipients()[0].toString()).isEqualTo(TO);
    assertThat(message.getSubject()).isEqualTo("重设你的 Patra 密码");
    assertThat(message.getHeader("Resend-Idempotency-Key")).containsExactly(EVENT_ID.toString());
    assertThat(message.getHeader("Auto-Submitted")).containsExactly("auto-generated");
    assertThat(message.getHeader("X-Auto-Response-Suppress")).containsExactly("All");
    assertThat(message.getHeader("List-Unsubscribe")).isNull();
    assertThat(message.getHeader("List-Unsubscribe-Post")).isNull();
    MimeMultipart alternative = findAlternative(message.getContent());
    assertThat(alternative).as("multipart/alternative 部分").isNotNull();
    assertThat(alternative.getCount()).isEqualTo(2);
    BodyPart text = alternative.getBodyPart(0);
    BodyPart html = alternative.getBodyPart(1);
    assertThat(text.getContentType()).startsWith("text/plain");
    assertThat(html.getContentType()).startsWith("text/html");
    assertThat(text.getContent().toString()).contains(RESET_URL).contains("陈宇");
    assertThat(html.getContent().toString()).contains("href=\"" + RESET_URL + "\"");
  }

  @Test
  @DisplayName("同一封信发两次，两次的幂等键相同：都是投递 ID")
  void should_use_same_idempotency_key_for_each_attempt() throws Exception {
    when(mailSender.createMimeMessage())
        .thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
    SmtpMailDeliveryAdapter adapter = new SmtpMailDeliveryAdapter(mailSender, renderer, FROM);

    adapter.deliver(mail);
    adapter.deliver(mail);

    ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender, times(2)).send(sent.capture());
    List<MimeMessage> messages = sent.getAllValues();
    assertThat(messages.get(0).getHeader("Resend-Idempotency-Key"))
        .containsExactly(messages.get(1).getHeader("Resend-Idempotency-Key"));
  }

  @Test
  @DisplayName("服务商拒收：转成发信失败，不挂原始异常；原因里有回复码与异常类名，收件地址被遮住")
  void should_wrap_send_failure_without_leaking_recipient() {
    when(mailSender.createMimeMessage())
        .thenReturn(new MimeMessage(Session.getInstance(new Properties())));
    doThrow(new MailSendException("550 5.1.1 <chen.yu@example.com>: Recipient address rejected"))
        .when(mailSender)
        .send(any(MimeMessage.class));
    SmtpMailDeliveryAdapter adapter = new SmtpMailDeliveryAdapter(mailSender, renderer, FROM);

    assertThatThrownBy(() -> adapter.deliver(mail))
        .isInstanceOf(MailDeliveryFailedException.class)
        .hasNoCause()
        .hasMessageContaining(EVENT_ID.toString())
        .hasMessageContaining("password-reset")
        .hasMessageContaining("550")
        .hasMessageContaining("MailSendException")
        .hasMessageNotContaining("chen.yu@example.com");
  }

  @Test
  @DisplayName("收件地址不合法：转成发信失败，不调发信器")
  void should_wrap_invalid_recipient() {
    when(mailSender.createMimeMessage())
        .thenReturn(new MimeMessage(Session.getInstance(new Properties())));
    OutgoingMail broken =
        OutgoingMail.builder()
            .deliveryId(EVENT_ID)
            .to("not an address")
            .template("password-reset")
            .subject("重设你的 Patra 密码")
            .model(mail.model())
            .build();
    SmtpMailDeliveryAdapter adapter = new SmtpMailDeliveryAdapter(mailSender, renderer, FROM);

    assertThatThrownBy(() -> adapter.deliver(broken))
        .isInstanceOf(MailDeliveryFailedException.class)
        .hasNoCause();
    verify(mailSender, never()).send(any(MimeMessage.class));
  }

  @Test
  @DisplayName("失败原因很长时截到 500 个字符")
  void should_truncate_long_failure_reason() {
    String reason = SmtpMailDeliveryAdapter.describe(new MailSendException("x".repeat(2_000)));

    assertThat(reason).hasSizeLessThanOrEqualTo(500);
  }

  @Test
  @DisplayName("失败原因沿 cause 链逐层写出类名与消息，各层的邮箱地址都被遮住")
  void should_describe_cause_chain_with_masked_addresses() {
    String reason =
        SmtpMailDeliveryAdapter.describe(
            new MailSendException(
                "Failed messages",
                new IllegalStateException("554 rejected for a.b+c@mail.example.org")));

    assertThat(reason)
        .contains("MailSendException: Failed messages")
        .contains("IllegalStateException: 554 rejected for ***")
        .doesNotContain("a.b+c@mail.example.org");
  }

  @Test
  @DisplayName("发件人不是恰好一个合法地址：构造时就失败")
  void should_reject_invalid_from_at_construction() {
    assertThatThrownBy(() -> new SmtpMailDeliveryAdapter(mailSender, renderer, "not an address"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new SmtpMailDeliveryAdapter(mailSender, renderer, "a@example.com, b@example.com"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /// 发一次，取出发信器收到的邮件（已 `saveChanges`）。
  ///
  /// @return 收到的邮件
  /// @throws Exception 取邮件内容失败
  private List<MimeMessage> deliverAndCapture() throws Exception {
    when(mailSender.createMimeMessage())
        .thenReturn(new MimeMessage(Session.getInstance(new Properties())));
    new SmtpMailDeliveryAdapter(mailSender, renderer, FROM).deliver(mail);
    ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(sent.capture());
    MimeMessage message = sent.getValue();
    message.saveChanges();
    return List.of(message);
  }

  /// 递归找出 `multipart/alternative` 部分：`MimeMessageHelper` 的多部分模式会在外面包 mixed / related。
  ///
  /// @param content 邮件或某一部分的内容
  /// @return alternative 部分；没有时为 `null`
  /// @throws Exception 取内容失败
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
