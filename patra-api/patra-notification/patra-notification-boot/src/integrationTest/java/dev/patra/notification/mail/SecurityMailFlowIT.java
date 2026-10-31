package dev.patra.notification.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.icegreen.greenmail.util.GreenMail;
import dev.patra.identity.api.account.event.AccountDeletionCancelledMailEvent;
import dev.patra.identity.api.account.event.AccountDeletionCompletedMailEvent;
import dev.patra.identity.api.account.event.AccountDeletionReminderMailEvent;
import dev.patra.identity.api.account.event.AccountDeletionRequestedMailEvent;
import dev.patra.identity.api.account.event.EmailChangeConfirmMailEvent;
import dev.patra.identity.api.account.event.EmailChangeRevertedMailEvent;
import dev.patra.identity.api.account.event.EmailChangedConfirmationMailEvent;
import dev.patra.identity.api.account.event.EmailChangedNoticeMailEvent;
import dev.patra.identity.api.account.event.EmailVerificationMailEvent;
import dev.patra.identity.api.account.event.NewDeviceLoginMailEvent;
import dev.patra.identity.api.account.event.OperationContext;
import dev.patra.identity.api.account.event.PasswordChangedMailEvent;
import dev.patra.identity.api.account.event.PasswordResetDoneMailEvent;
import dev.patra.identity.api.account.event.PasswordResetMailEvent;
import dev.patra.identity.api.account.event.SecurityMailEvent;
import dev.patra.notification.domain.mail.port.repository.MailDeliveryRepository;
import dev.patra.tool.test.mail.GreenMailSmtpInitializer;
import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;

/// 安全邮件端到端：identity 形状的事件进 `notification.security-mail`，经 SMTP（GreenMail）发出，
/// 记发送记录与已处理事件（spec 第 12 节）。
@ContextConfiguration(initializers = GreenMailSmtpInitializer.class)
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("安全邮件端到端")
class SecurityMailFlowIT extends SecurityMailITSupport {

  private static final Instant LATER = Instant.parse("2099-01-01T00:00:00Z");
  private static final OperationContext CONTEXT =
      OperationContext.of(Instant.parse("2026-10-31T01:00:00Z"), "Chrome 141 · macOS", null, null);
  private static final String SECURITY_URL = "https://patra.example/settings/security";

  private final GreenMail greenMail = GreenMailSmtpInitializer.getGreenMail();

  @MockitoSpyBean private MailDeliveryRepository deliveries;
  @MockitoSpyBean private JavaMailSender mailSender;

  /// 用例之间清空收件箱。
  @BeforeEach
  void purgeMailboxes() {
    GreenMailSmtpInitializer.purge();
  }

  /// 13 类各发一个事件：每个地址收到一封、主题对、幂等键就是事件 ID、没有退订头；发送记录与已处理各 13 行。
  @Test
  void should_deliver_all_thirteen_kinds() throws Exception {
    List<Sample> samples = thirteen();
    samples.forEach(sample -> publish(sample.event(), sample.routingKey()));

    assertThat(greenMail.waitForIncomingEmail(20_000, 13)).isTrue();
    for (Sample sample : samples) {
      List<MimeMessage> mails = mailsTo(sample.event().to());
      assertThat(mails).as(sample.routingKey()).hasSize(1);
      MimeMessage mail = mails.getFirst();
      assertThat(mail.getSubject()).as(sample.routingKey()).isEqualTo(sample.subject());
      assertThat(mail.getHeader("Resend-Idempotency-Key"))
          .as(sample.routingKey())
          .containsExactly(sample.event().eventId().toString());
      assertThat(mail.getHeader("Message-ID"))
          .as(sample.routingKey())
          .containsExactly("<" + sample.event().eventId() + "@patra.local>");
      assertThat(mail.getHeader("List-Unsubscribe")).as(sample.routingKey()).isNull();
      assertThat(mail.getHeader("List-Unsubscribe-Post")).as(sample.routingKey()).isNull();
    }
    List<UUID> ids = samples.stream().map(sample -> sample.event().eventId()).toList();
    await().atMost(Duration.ofSeconds(10)).until(() -> deliveryRows(ids) == 13);
    assertThat(ids).allSatisfy(id -> assertThat(processedRows(id)).isEqualTo(1));
  }

  /// 换邮箱：确认信到新地址，变更通知到旧地址（D6）。
  @Test
  void should_send_change_mails_to_new_and_old_addresses() throws Exception {
    publish(
        EmailChangeConfirmMailEvent.builder()
            .eventId(UUID.randomUUID())
            .to("new@example.com")
            .displayName("陈宇")
            .confirmUrl("https://patra.example/confirm-email-change#token=c")
            .linkExpiresAt(LATER)
            .build(),
        EmailChangeConfirmMailEvent.ROUTING_KEY);
    publish(
        EmailChangedNoticeMailEvent.builder()
            .eventId(UUID.randomUUID())
            .to("old@example.com")
            .displayName("陈宇")
            .newEmailMasked("n***@example.com")
            .revertUrl("https://patra.example/revert-email-change#token=r")
            .linkExpiresAt(LATER)
            .securitySettingsUrl(SECURITY_URL)
            .context(CONTEXT)
            .build(),
        EmailChangedNoticeMailEvent.ROUTING_KEY);

    assertThat(greenMail.waitForIncomingEmail(10_000, 2)).isTrue();
    assertThat(mailsTo("new@example.com"))
        .singleElement()
        .satisfies(mail -> assertThat(mail.getSubject()).isEqualTo("确认你的新 Patra 邮箱"));
    assertThat(mailsTo("old@example.com"))
        .singleElement()
        .satisfies(mail -> assertThat(mail.getSubject()).isEqualTo("你的 Patra 邮箱已更改"));
  }

  /// 同一个事件投两次只收到一封、一行记录：后面再发一个哨兵，它的信到了说明前两条已处理完（单消费者、按序）。
  @Test
  void should_deliver_duplicate_event_once() throws Exception {
    PasswordResetMailEvent event = passwordReset("dup@example.com", LATER);
    publish(event, PasswordResetMailEvent.ROUTING_KEY);
    publish(event, PasswordResetMailEvent.ROUTING_KEY);
    publish(passwordReset("sentinel@example.com", LATER), PasswordResetMailEvent.ROUTING_KEY);

    await().atMost(Duration.ofSeconds(10)).until(() -> !mailsTo("sentinel@example.com").isEmpty());
    assertThat(mailsTo("dup@example.com")).hasSize(1);
    assertThat(deliveryRows(event.eventId())).isEqualTo(1);
  }

  /// 信已发出、记录时失败：重试再发一次，两封的幂等键相同（Resend 据此只送达一次），最后一行记录。
  @Test
  void should_keep_idempotency_key_when_recording_fails_after_send() throws Exception {
    // 仓储带 MANDATORY 的事务代理，桩要打在代理里面的 spy 上，否则打桩这一下就会被事务守卫拦住
    MailDeliveryRepository spy = AopTestUtils.getUltimateTargetObject(deliveries);
    doThrow(new IllegalStateException("记录失败")).doCallRealMethod().when(spy).add(any());
    PasswordResetMailEvent event = passwordReset("retry-record@example.com", LATER);

    publish(event, PasswordResetMailEvent.ROUTING_KEY);

    awaitDelivery(event.eventId());
    List<MimeMessage> mails = mailsTo("retry-record@example.com");
    assertThat(mails).hasSize(2);
    assertThat(mails.get(0).getHeader("Resend-Idempotency-Key"))
        .containsExactly(event.eventId().toString());
    assertThat(mails.get(1).getHeader("Resend-Idempotency-Key"))
        .containsExactly(event.eventId().toString());
    assertThat(mails.get(1).getHeader("Message-ID"))
        .isEqualTo(mails.get(0).getHeader("Message-ID"));
    assertThat(mails.get(1).getSubject()).isEqualTo(mails.get(0).getSubject());
    assertThat(bodyParts(mails.get(1))).isEqualTo(bodyParts(mails.get(0)));
  }

  /// SMTP 临时不通：第一次发送失败，按退避重试后发出，只记一行。
  @Test
  void should_retry_and_deliver_after_transient_failure() throws Exception {
    doThrow(new MailSendException("421 服务暂时不可用"))
        .doCallRealMethod()
        .when(mailSender)
        .send(any(MimeMessage.class));
    PasswordResetMailEvent event = passwordReset("transient@example.com", LATER);

    publish(event, PasswordResetMailEvent.ROUTING_KEY);

    awaitDelivery(event.eventId());
    assertThat(mailsTo("transient@example.com")).hasSize(1);
    verify(mailSender, times(2)).send(any(MimeMessage.class));
  }

  /// 服务商永久拒收（550）：不重试、不进死信，记 `REJECTED`，只调了一次发信器。
  @Test
  void should_record_rejected_recipient_without_retry() {
    Map<Object, Exception> failed = new LinkedHashMap<>();
    failed.put(
        new Object(),
        new SendFailedException(
            "Invalid Addresses",
            new MessagingException(
                "550 5.1.1 <rejected@example.com>: Recipient address rejected")));
    doThrow(new MailSendException(failed)).when(mailSender).send(any(MimeMessage.class));
    PasswordResetMailEvent event = passwordReset("rejected@example.com", LATER);

    publish(event, PasswordResetMailEvent.ROUTING_KEY);

    assertThat(awaitDelivery(event.eventId())).containsEntry("outcome", "REJECTED");
    assertThat(processedRows(event.eventId())).isEqualTo(1);
    verify(mailSender, times(1)).send(any(MimeMessage.class));
    assertThat(rabbitTemplate.receive(DEAD_LETTER_QUEUE, 500)).isNull();
  }

  /// 没有类型头、类型头写的是 `java.util` / `java.lang` 下的类型（Jackson 总是信任这两个包，`java.lang.Object`
  /// 会把正文读成 Map）：都进死信，日志里没有收件地址与链接，后面的正常事件照发。
  /// 例子：在管理界面手动重放死信时默认不带头；别的发布方没用 Jackson 转换器。
  @Test
  void should_dead_letter_untyped_payload_without_leaking_it(CapturedOutput output) {
    PasswordResetMailEvent untyped = passwordReset("untyped@example.com", LATER);
    PasswordResetMailEvent mapTyped = passwordReset("map-typed@example.com", LATER);
    PasswordResetMailEvent objectTyped = passwordReset("object-typed@example.com", LATER);

    sendRaw(untyped, null);
    sendRaw(mapTyped, "java.util.HashMap");
    sendRaw(objectTyped, "java.lang.Object");

    assertThat(rabbitTemplate.receive(DEAD_LETTER_QUEUE, 15_000)).isNotNull();
    assertThat(rabbitTemplate.receive(DEAD_LETTER_QUEUE, 15_000)).isNotNull();
    assertThat(rabbitTemplate.receive(DEAD_LETTER_QUEUE, 15_000)).isNotNull();
    PasswordResetMailEvent valid = passwordReset("after-poison@example.com", LATER);
    publish(valid, PasswordResetMailEvent.ROUTING_KEY);
    awaitDelivery(valid.eventId());
    assertThat(mailsTo("untyped@example.com")).isEmpty();
    assertThat(output.getAll())
        .doesNotContain("untyped@example.com")
        .doesNotContain("map-typed@example.com")
        .doesNotContain("object-typed@example.com")
        .doesNotContain(untyped.resetUrl())
        .doesNotContain(mapTyped.resetUrl())
        .doesNotContain(objectTyped.resetUrl())
        .doesNotContain("token=");
  }

  /// 发送记录里没有正文、链接与收件地址。
  @Test
  void should_store_no_body_link_or_address() throws Exception {
    PasswordResetMailEvent event = passwordReset("record@example.com", LATER);

    publish(event, PasswordResetMailEvent.ROUTING_KEY);

    Map<String, Object> row = awaitDelivery(event.eventId());
    assertThat(row).containsOnlyKeys("delivery_id", "template", "outcome", "recorded_at");
    assertThat(row.values())
        .allSatisfy(
            value ->
                assertThat(String.valueOf(value))
                    .doesNotContain("record@example.com")
                    .doesNotContain(event.resetUrl())
                    .doesNotContain("token="));
    assertThat(row).containsEntry("template", "password-reset").containsEntry("outcome", "SENT");
  }

  /// 找回密码的链接已过期：不发信，记 `LINK_EXPIRED`。
  @Test
  void should_skip_expired_reset_link() throws Exception {
    PasswordResetMailEvent event =
        passwordReset("expired@example.com", Instant.parse("2020-01-01T00:00:00Z"));

    publish(event, PasswordResetMailEvent.ROUTING_KEY);

    assertThat(awaitDelivery(event.eventId())).containsEntry("outcome", "LINK_EXPIRED");
    assertThat(mailsTo("expired@example.com")).isEmpty();
  }

  /// 照 identity 的 JSON 正文发一条消息，但类型头换成 `typeId`（`null` 表示不带类型头）。
  ///
  /// @param event 事件
  /// @param typeId 类型头的值
  private void sendRaw(PasswordResetMailEvent event, String typeId) {
    Message message =
        rabbitTemplate.getMessageConverter().toMessage(event, new MessageProperties());
    message.getMessageProperties().getHeaders().remove("__TypeId__");
    if (typeId != null) {
      message.getMessageProperties().setHeader("__TypeId__", typeId);
    }
    message.getMessageProperties().setMessageId(event.eventId().toString());
    rabbitTemplate.send(SecurityMailEvent.EXCHANGE, PasswordResetMailEvent.ROUTING_KEY, message);
  }

  /// 一封信的纯文本与 HTML 两部分的内容（不含 MIME 分隔线，那是每次发送随机生成的）。
  ///
  /// @param mail 信
  /// @return 各部分的内容，按出现顺序
  /// @throws Exception 读不出内容
  private static List<String> bodyParts(MimeMessage mail) throws Exception {
    List<String> parts = new ArrayList<>();
    collectParts(mail.getContent(), parts);
    return parts;
  }

  /// 递归取出多部分里每个文本部分的内容。
  ///
  /// @param content 内容
  /// @param parts 收集到的文本
  /// @throws Exception 读不出内容
  private static void collectParts(Object content, List<String> parts) throws Exception {
    if (content instanceof Multipart multipart) {
      for (int i = 0; i < multipart.getCount(); i++) {
        collectParts(multipart.getBodyPart(i).getContent(), parts);
      }
    } else {
      parts.add(String.valueOf(content));
    }
  }

  /// GreenMail 收到的、收件人是 `address` 的信。
  ///
  /// @param address 收件地址
  /// @return 信
  private List<MimeMessage> mailsTo(String address) {
    return Arrays.stream(greenMail.getReceivedMessages())
        .filter(mail -> recipients(mail).contains(address))
        .toList();
  }

  /// 一封信的收件人地址。
  ///
  /// @param mail 信
  /// @return 地址
  private static List<String> recipients(MimeMessage mail) {
    try {
      Address[] all = mail.getAllRecipients();
      return all == null ? List.of() : Arrays.stream(all).map(Address::toString).toList();
    } catch (Exception e) {
      throw new IllegalStateException("读不到收件人", e);
    }
  }

  /// 13 类各一个事件，收件地址各不相同。
  ///
  /// @return 样本
  private static List<Sample> thirteen() {
    return List.of(
        new Sample(
            EmailVerificationMailEvent.builder()
                .eventId(UUID.randomUUID())
                .to("verify@example.com")
                .displayName("陈宇")
                .verifyUrl("https://patra.example/verify-email#token=v")
                .linkExpiresAt(LATER)
                .build(),
            EmailVerificationMailEvent.ROUTING_KEY,
            "验证你的 Patra 邮箱"),
        new Sample(
            passwordReset("reset@example.com", LATER),
            PasswordResetMailEvent.ROUTING_KEY,
            "重设你的 Patra 密码"),
        new Sample(
            PasswordResetDoneMailEvent.of(
                UUID.randomUUID(), "reset-done@example.com", "陈宇", CONTEXT),
            PasswordResetDoneMailEvent.ROUTING_KEY,
            "你的 Patra 密码已重设"),
        new Sample(
            PasswordChangedMailEvent.of(UUID.randomUUID(), "changed@example.com", "陈宇", CONTEXT),
            PasswordChangedMailEvent.ROUTING_KEY,
            "你的 Patra 密码已修改"),
        new Sample(
            EmailChangeConfirmMailEvent.builder()
                .eventId(UUID.randomUUID())
                .to("confirm@example.com")
                .displayName("陈宇")
                .confirmUrl("https://patra.example/confirm-email-change#token=c")
                .linkExpiresAt(LATER)
                .build(),
            EmailChangeConfirmMailEvent.ROUTING_KEY,
            "确认你的新 Patra 邮箱"),
        new Sample(
            EmailChangedNoticeMailEvent.builder()
                .eventId(UUID.randomUUID())
                .to("notice@example.com")
                .displayName("陈宇")
                .newEmailMasked("n***@example.com")
                .revertUrl("https://patra.example/revert-email-change#token=r")
                .linkExpiresAt(LATER)
                .securitySettingsUrl(SECURITY_URL)
                .context(CONTEXT)
                .build(),
            EmailChangedNoticeMailEvent.ROUTING_KEY,
            "你的 Patra 邮箱已更改"),
        new Sample(
            EmailChangedConfirmationMailEvent.builder()
                .eventId(UUID.randomUUID())
                .to("bound@example.com")
                .displayName("陈宇")
                .securitySettingsUrl(SECURITY_URL)
                .context(CONTEXT)
                .build(),
            EmailChangedConfirmationMailEvent.ROUTING_KEY,
            "本邮箱已绑定你的 Patra 账号"),
        new Sample(
            EmailChangeRevertedMailEvent.builder()
                .eventId(UUID.randomUUID())
                .to("reverted@example.com")
                .displayName("陈宇")
                .resetUrl("https://patra.example/reset-password#token=x")
                .linkExpiresAt(LATER)
                .context(CONTEXT)
                .build(),
            EmailChangeRevertedMailEvent.ROUTING_KEY,
            "你的 Patra 邮箱已改回，请重设密码"),
        new Sample(
            NewDeviceLoginMailEvent.builder()
                .eventId(UUID.randomUUID())
                .to("device@example.com")
                .displayName("陈宇")
                .securitySettingsUrl(SECURITY_URL)
                .context(CONTEXT)
                .build(),
            NewDeviceLoginMailEvent.ROUTING_KEY,
            "你的 Patra 账号在新设备上登录"),
        new Sample(
            AccountDeletionRequestedMailEvent.builder()
                .eventId(UUID.randomUUID())
                .to("deletion-requested@example.com")
                .displayName("陈宇")
                .deletionDueAt(LATER)
                .context(CONTEXT)
                .build(),
            AccountDeletionRequestedMailEvent.ROUTING_KEY,
            "你的 Patra 账号将被注销"),
        new Sample(
            AccountDeletionCancelledMailEvent.of(
                UUID.randomUUID(), "deletion-cancelled@example.com", "陈宇", CONTEXT),
            AccountDeletionCancelledMailEvent.ROUTING_KEY,
            "你的 Patra 账号已取消注销"),
        new Sample(
            AccountDeletionReminderMailEvent.builder()
                .eventId(UUID.randomUUID())
                .to("deletion-reminder@example.com")
                .displayName("陈宇")
                .deletionDueAt(LATER)
                .daysLeft(3)
                .build(),
            AccountDeletionReminderMailEvent.ROUTING_KEY,
            "你的 Patra 账号将在 3 天后删除"),
        new Sample(
            AccountDeletionCompletedMailEvent.of(
                UUID.randomUUID(), "deletion-completed@example.com", "陈宇", "chen-yu"),
            AccountDeletionCompletedMailEvent.ROUTING_KEY,
            "你的 Patra 账号已注销"));
  }

  /// 一个样本：事件、路由键、期望的主题。
  ///
  /// @param event 事件
  /// @param routingKey 路由键
  /// @param subject 期望的主题
  private record Sample(SecurityMailEvent event, String routingKey, String subject) {}
}
