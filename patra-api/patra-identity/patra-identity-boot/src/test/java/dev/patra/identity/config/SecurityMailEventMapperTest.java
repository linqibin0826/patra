package dev.patra.identity.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
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
import dev.patra.identity.api.account.event.PasswordResetMailEvent;
import dev.patra.identity.api.account.event.SecurityMailEvent;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.mail.AccountDeletionCompletedMail;
import dev.patra.identity.domain.account.model.vo.mail.AccountDeletionReminderMail;
import dev.patra.identity.domain.account.model.vo.mail.AccountDeletionRequestedMail;
import dev.patra.identity.domain.account.model.vo.mail.EmailChangeConfirmMail;
import dev.patra.identity.domain.account.model.vo.mail.EmailChangeRevertedMail;
import dev.patra.identity.domain.account.model.vo.mail.EmailChangedConfirmationMail;
import dev.patra.identity.domain.account.model.vo.mail.EmailChangedNoticeMail;
import dev.patra.identity.domain.account.model.vo.mail.EmailVerificationMail;
import dev.patra.identity.domain.account.model.vo.mail.NewDeviceLoginMail;
import dev.patra.identity.domain.account.model.vo.mail.OutgoingMail;
import dev.patra.identity.domain.account.model.vo.mail.PasswordResetMail;
import dev.patra.identity.domain.account.policy.MailLinkPolicy;
import dev.patra.identity.infra.account.mail.SecurityMailFixture;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// SecurityMailEventMapper 单元测试：13 种安全邮件逐个映射成集成事件，事件 ID 原样、字段齐全、路由键各不相同。
@DisplayName("SecurityMailEventMapper 单元测试")
class SecurityMailEventMapperTest {

  private static final MailLinkPolicy LINKS =
      MailLinkPolicy.builder()
          .baseUrl("https://patra.example")
          .verifyEmailPath("/verify-email")
          .securitySettingsPath("/settings/security")
          .resetPasswordPath("/reset-password")
          .confirmEmailChangePath("/confirm-email-change")
          .revertEmailChangePath("/revert-email-change")
          .build();

  private final SecurityMailEventMapper mapper = new SecurityMailEventMapper(LINKS);

  @Test
  @DisplayName("每封邮件：事件 ID、收件地址、昵称原样带进集成事件")
  void should_map_every_mail_with_same_event_id_and_recipient() {
    for (OutgoingMail mail : SecurityMailFixture.all()) {
      SecurityMailEvent event = mapper.toIntegrationEvent(mail);

      assertThat(event.eventId()).as(mail.getClass().getSimpleName()).isEqualTo(mail.eventId());
      assertThat(event.to()).isEqualTo(mail.to().value());
      assertThat(event.displayName()).isEqualTo("陈宇");
    }
  }

  @Test
  @DisplayName("带一次性令牌的五封：链接与链接过期时间原样带上")
  void should_carry_links_and_expiry_for_token_mails() {
    Map<Class<?>, OutgoingMail> mails = byType();

    EmailVerificationMail verification =
        (EmailVerificationMail) mails.get(EmailVerificationMail.class);
    EmailVerificationMailEvent verificationEvent =
        (EmailVerificationMailEvent) mapper.toIntegrationEvent(verification);
    assertThat(verificationEvent.verifyUrl()).isEqualTo(verification.verifyUrl());
    assertThat(verificationEvent.linkExpiresAt()).isEqualTo(SecurityMailFixture.LINK_EXPIRES);

    PasswordResetMail reset = (PasswordResetMail) mails.get(PasswordResetMail.class);
    PasswordResetMailEvent resetEvent = (PasswordResetMailEvent) mapper.toIntegrationEvent(reset);
    assertThat(resetEvent.resetUrl()).isEqualTo(reset.resetUrl());
    assertThat(resetEvent.linkExpiresAt()).isEqualTo(SecurityMailFixture.LINK_EXPIRES);

    EmailChangeConfirmMail confirm =
        (EmailChangeConfirmMail) mails.get(EmailChangeConfirmMail.class);
    EmailChangeConfirmMailEvent confirmEvent =
        (EmailChangeConfirmMailEvent) mapper.toIntegrationEvent(confirm);
    assertThat(confirmEvent.to()).as("确认信发到新邮箱").isEqualTo(SecurityMailFixture.NEW_EMAIL.value());
    assertThat(confirmEvent.confirmUrl()).isEqualTo(confirm.confirmUrl());
    assertThat(confirmEvent.linkExpiresAt()).isEqualTo(SecurityMailFixture.LINK_EXPIRES);

    EmailChangedNoticeMail notice =
        (EmailChangedNoticeMail) mails.get(EmailChangedNoticeMail.class);
    EmailChangedNoticeMailEvent noticeEvent =
        (EmailChangedNoticeMailEvent) mapper.toIntegrationEvent(notice);
    assertThat(noticeEvent.to()).as("变更通知发到旧邮箱").isEqualTo(SecurityMailFixture.TO.value());
    assertThat(noticeEvent.newEmailMasked()).isEqualTo("c***@example.com");
    assertThat(noticeEvent.revertUrl()).isEqualTo(notice.revertUrl());
    assertThat(noticeEvent.linkExpiresAt()).isEqualTo(SecurityMailFixture.LINK_EXPIRES);

    EmailChangeRevertedMail reverted =
        (EmailChangeRevertedMail) mails.get(EmailChangeRevertedMail.class);
    EmailChangeRevertedMailEvent revertedEvent =
        (EmailChangeRevertedMailEvent) mapper.toIntegrationEvent(reverted);
    assertThat(revertedEvent.resetUrl()).isEqualTo(reverted.resetUrl());
    assertThat(revertedEvent.linkExpiresAt()).isEqualTo(SecurityMailFixture.LINK_EXPIRES);
  }

  @Test
  @DisplayName("安全通知：时间、设备、IP、属地进 context，「不是我」文案不进；缺失的仍为 null")
  void should_map_notice_into_operation_context() {
    EmailChangedNoticeMailEvent full =
        (EmailChangedNoticeMailEvent)
            mapper.toIntegrationEvent(byType().get(EmailChangedNoticeMail.class));
    assertThat(full.context())
        .isEqualTo(
            OperationContext.of(
                SecurityMailFixture.NOTICE.occurredAt(),
                "Chrome 141 · macOS",
                "113.92.157.29",
                "中国 广东 深圳"));

    NewDeviceLoginMailEvent bare =
        (NewDeviceLoginMailEvent) mapper.toIntegrationEvent(byType().get(NewDeviceLoginMail.class));
    assertThat(bare.context())
        .isEqualTo(OperationContext.of(SecurityMailFixture.NOTICE.occurredAt(), null, null, null));
  }

  @Test
  @DisplayName("安全设置链接只放进用得到它的三封：旧邮箱通知、新邮箱「已绑定」、新设备登录")
  void should_add_security_settings_url_where_templates_use_it() {
    Map<Class<?>, OutgoingMail> mails = byType();
    String security = "https://patra.example/settings/security";

    assertThat(
            ((EmailChangedNoticeMailEvent)
                    mapper.toIntegrationEvent(mails.get(EmailChangedNoticeMail.class)))
                .securitySettingsUrl())
        .isEqualTo(security);
    EmailChangedConfirmationMailEvent bound =
        (EmailChangedConfirmationMailEvent)
            mapper.toIntegrationEvent(mails.get(EmailChangedConfirmationMail.class));
    assertThat(bound.securitySettingsUrl()).isEqualTo(security);
    assertThat(bound.to()).as("「已绑定」发到新邮箱").isEqualTo(SecurityMailFixture.NEW_EMAIL.value());
    assertThat(
            ((NewDeviceLoginMailEvent)
                    mapper.toIntegrationEvent(mails.get(NewDeviceLoginMail.class)))
                .securitySettingsUrl())
        .isEqualTo(security);
  }

  @Test
  @DisplayName("注销的三封：到期时刻、剩余天数、用户名原样带上")
  void should_carry_deletion_facts() {
    Map<Class<?>, OutgoingMail> mails = byType();

    AccountDeletionRequestedMailEvent requested =
        (AccountDeletionRequestedMailEvent)
            mapper.toIntegrationEvent(mails.get(AccountDeletionRequestedMail.class));
    assertThat(requested.deletionDueAt()).isEqualTo(SecurityMailFixture.DUE);
    AccountDeletionReminderMailEvent reminder =
        (AccountDeletionReminderMailEvent)
            mapper.toIntegrationEvent(mails.get(AccountDeletionReminderMail.class));
    assertThat(reminder.deletionDueAt()).isEqualTo(SecurityMailFixture.DUE);
    assertThat(reminder.daysLeft()).isEqualTo(3);
    AccountDeletionCompletedMailEvent completed =
        (AccountDeletionCompletedMailEvent)
            mapper.toIntegrationEvent(mails.get(AccountDeletionCompletedMail.class));
    assertThat(completed.username()).isEqualTo("chen-yu");
  }

  @Test
  @DisplayName("路由键：13 个各不相同，security-mail. 加邮件种类（与 identity 原来的模板名一一对应），都匹配一条 security-mail.* 绑定")
  void should_route_every_event_under_security_mail_prefix() {
    List<OutgoingMail> mails = SecurityMailFixture.all();

    List<String> keys =
        mails.stream()
            .map(mail -> SecurityMailEventMapper.routingKeyOf(mapper.toIntegrationEvent(mail)))
            .toList();

    assertThat(keys)
        .containsExactly(
            "security-mail.email-verification",
            "security-mail.password-reset",
            "security-mail.password-reset-done",
            "security-mail.password-changed",
            "security-mail.email-change-confirm",
            "security-mail.email-changed-notice",
            "security-mail.email-changed-confirmation",
            "security-mail.email-change-reverted",
            "security-mail.new-device-login",
            "security-mail.account-deletion-requested",
            "security-mail.account-deletion-cancelled",
            "security-mail.account-deletion-reminder",
            "security-mail.account-deletion-completed")
        .allSatisfy(key -> assertThat(key).matches("security-mail\\.[a-z-]+"));
    assertThat(SecurityMailEvent.ROUTING_KEY_PATTERN).isEqualTo("security-mail.*");
    assertThat(SecurityMailEvent.EXCHANGE).isEqualTo("identity.events");
  }

  @Test
  @DisplayName("domain 里每一种 OutgoingMail 都有样本、都能映射：新增第 14 种忘了映射时这里失败")
  void should_cover_every_outgoing_mail_type() {
    Set<String> implementations =
        new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("dev.patra.identity.domain")
                .stream()
                .filter(type -> type.isAssignableTo(OutgoingMail.class) && !type.isInterface())
                .map(JavaClass::getName)
                .collect(Collectors.toSet());

    assertThat(byType().keySet().stream().map(Class::getName).collect(Collectors.toSet()))
        .isEqualTo(implementations);
  }

  @Test
  @DisplayName("集成事件的 toString 只有事件 ID，不出现收件地址与链接")
  void should_not_leak_recipient_or_link_in_integration_event_to_string() {
    for (OutgoingMail mail : SecurityMailFixture.all()) {
      SecurityMailEvent event = mapper.toIntegrationEvent(mail);

      assertThat(event.toString())
          .as(event.getClass().getSimpleName())
          .contains(event.eventId().toString())
          .doesNotContain("chen.")
          .doesNotContain("token=");
    }
  }

  @Test
  @DisplayName("不认识的邮件类型：抛 IllegalArgumentException，不悄悄外送")
  void should_reject_unknown_mail_type() {
    OutgoingMail unknown =
        new OutgoingMail() {
          @Override
          public UUID eventId() {
            return UUID.randomUUID();
          }

          @Override
          public EmailAddress to() {
            return SecurityMailFixture.TO;
          }
        };

    assertThatThrownBy(() -> mapper.toIntegrationEvent(unknown))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /// 样本按类型索引。
  ///
  /// @return 类型到样本
  private static Map<Class<?>, OutgoingMail> byType() {
    return SecurityMailFixture.all().stream()
        .collect(Collectors.toMap(Object::getClass, mail -> mail));
  }
}
