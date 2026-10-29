package dev.patra.identity.infra.account.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.mail.EmailVerificationMail;
import dev.patra.identity.domain.account.policy.MailLinkPolicy;
import jakarta.mail.BodyPart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

/// SmtpEmailDeliveryAdapter 单元测试：MIME 结构与头、失败只记日志、交给执行器、发件人校验。
@DisplayName("SmtpEmailDeliveryAdapter 单元测试")
@ExtendWith(OutputCaptureExtension.class)
class SmtpEmailDeliveryAdapterTest {

  private static final String FROM = "Patra <no-reply@patra.local>";
  private static final String VERIFY_URL = "https://patra.example/verify-email#token=abc";

  private final JavaMailSender mailSender = mock(JavaMailSender.class);
  private final ThymeleafMailRenderer renderer =
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
  private final EmailVerificationMail mail =
      EmailVerificationMail.of(
          EmailAddress.of("chen.yu@example.com"),
          "陈宇",
          VERIFY_URL,
          Instant.parse("2026-10-22T08:00:00Z"));

  @Test
  @DisplayName("同步执行器下发出一封 multipart/alternative：发件人、收件人、主题、两个自动邮件头、文本在前 HTML 在后")
  void should_send_multipart_alternative_with_auto_headers() throws Exception {
    when(mailSender.createMimeMessage())
        .thenReturn(new MimeMessage(Session.getInstance(new Properties())));
    SmtpEmailDeliveryAdapter adapter =
        new SmtpEmailDeliveryAdapter(mailSender, renderer, FROM, Runnable::run);

    adapter.send(mail);

    ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(sent.capture());
    MimeMessage message = sent.getValue();
    message.saveChanges();
    assertThat(message.getFrom()[0].toString()).isEqualTo(FROM);
    assertThat(message.getAllRecipients()).hasSize(1);
    assertThat(message.getAllRecipients()[0].toString()).isEqualTo("chen.yu@example.com");
    assertThat(message.getSubject()).isEqualTo("验证你的 Patra 邮箱");
    assertThat(message.getHeader("Auto-Submitted")).containsExactly("auto-generated");
    assertThat(message.getHeader("X-Auto-Response-Suppress")).containsExactly("All");
    MimeMultipart alternative = findAlternative(message.getContent());
    assertThat(alternative).as("multipart/alternative 部分").isNotNull();
    assertThat(alternative.getCount()).isEqualTo(2);
    BodyPart text = alternative.getBodyPart(0);
    BodyPart html = alternative.getBodyPart(1);
    assertThat(text.getContentType()).startsWith("text/plain");
    assertThat(html.getContentType()).startsWith("text/html");
    assertThat(text.getContent().toString()).contains(VERIFY_URL).contains("陈宇");
    assertThat(html.getContent().toString()).contains("href=\"" + VERIFY_URL + "\"");
  }

  @Test
  @DisplayName("发送失败只记 WARN，不抛给调用方，日志里有模板名、没有收件人")
  void should_log_and_swallow_failures(CapturedOutput output) {
    when(mailSender.createMimeMessage())
        .thenReturn(new MimeMessage(Session.getInstance(new Properties())));
    doThrow(new MailSendException("boom")).when(mailSender).send(any(MimeMessage.class));
    SmtpEmailDeliveryAdapter adapter =
        new SmtpEmailDeliveryAdapter(mailSender, renderer, FROM, Runnable::run);

    assertThatCode(() -> adapter.send(mail)).doesNotThrowAnyException();

    assertThat(output.getAll())
        .contains("邮件发送失败")
        .contains("template=email-verification")
        .doesNotContain("chen.yu@example.com");
  }

  @Test
  @DisplayName("send 只把工作交给执行器就返回；执行器跑了任务才真的发")
  void should_hand_work_to_executor() {
    when(mailSender.createMimeMessage())
        .thenReturn(new MimeMessage(Session.getInstance(new Properties())));
    List<Runnable> tasks = new ArrayList<>();
    Executor recording = tasks::add;
    SmtpEmailDeliveryAdapter adapter =
        new SmtpEmailDeliveryAdapter(mailSender, renderer, FROM, recording);

    adapter.send(mail);

    assertThat(tasks).hasSize(1);
    verify(mailSender, never()).send(any(MimeMessage.class));
    tasks.getFirst().run();
    verify(mailSender).send(any(MimeMessage.class));
  }

  @Test
  @DisplayName("交给执行器返回 true；执行器拒绝（队列满、已停机）返回 false，不抛出")
  void should_report_whether_executor_accepted_the_mail() {
    SmtpEmailDeliveryAdapter accepting =
        new SmtpEmailDeliveryAdapter(mailSender, renderer, FROM, task -> {});
    SmtpEmailDeliveryAdapter rejecting =
        new SmtpEmailDeliveryAdapter(
            mailSender,
            renderer,
            FROM,
            task -> {
              throw new RejectedExecutionException("队列满");
            });

    assertThat(accepting.send(mail)).isTrue();
    assertThat(rejecting.send(mail)).isFalse();
  }

  @Test
  @DisplayName("自带执行器：发送槽全被占住时 send 照样立即返回，不阻塞请求线程")
  void should_not_block_caller_when_all_senders_are_busy() throws Exception {
    when(mailSender.createMimeMessage())
        .thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch started = new CountDownLatch(1);
    org.mockito.Mockito.doAnswer(
            invocation -> {
              started.countDown();
              release.await(5, TimeUnit.SECONDS);
              return null;
            })
        .when(mailSender)
        .send(any(MimeMessage.class));
    SmtpEmailDeliveryAdapter adapter =
        SmtpEmailDeliveryAdapter.async(mailSender, renderer, FROM, 1);
    try {
      adapter.send(mail);
      assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

      long begin = System.nanoTime();
      adapter.send(mail);
      long elapsedMillis = (System.nanoTime() - begin) / 1_000_000;

      assertThat(elapsedMillis).isLessThan(500);
    } finally {
      release.countDown();
      adapter.close();
    }
  }

  @Test
  @DisplayName("发件人不是合法地址：构造时就失败")
  void should_reject_invalid_from_address() {
    assertThatThrownBy(
            () ->
                new SmtpEmailDeliveryAdapter(mailSender, renderer, "not an address", Runnable::run))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new SmtpEmailDeliveryAdapter(
                    mailSender, renderer, "a@example.com, b@example.com", Runnable::run))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /// 递归找出 `multipart/alternative` 部分：`MimeMessageHelper` 的多部分模式会在外面包 mixed / related。
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
