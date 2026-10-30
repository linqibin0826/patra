package dev.patra.notification.adapter.mail.mq;

import static org.assertj.core.api.Assertions.assertThat;

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
import dev.patra.notification.domain.mail.model.enums.SecurityMailKind;
import dev.patra.notification.domain.mail.model.vo.SecurityMail;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// SecurityMailEventConverter 单元测试：13 种事件各自转成对的类别、收件人、模板变量、操作上下文与链接过期时刻。
@DisplayName("SecurityMailEventConverter 单元测试")
class SecurityMailEventConverterTest {

  private static final UUID ID = UUID.fromString("7a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d");
  private static final String TO = "chen.yu@example.com";
  private static final String NAME = "陈宇";
  private static final Instant EXPIRES = Instant.parse("2026-10-31T02:30:00Z");
  private static final Instant DUE = Instant.parse("2026-11-14T00:00:00Z");
  private static final String SECURITY = "https://patra.example/settings/security";
  private static final OperationContext CONTEXT =
      OperationContext.of(
          Instant.parse("2026-10-31T01:00:00Z"), "Chrome 131 · macOS", "203.0.113.9", "中国 上海");

  /// 验证邮箱：验证链接与过期时刻，没有操作上下文。
  @Test
  void should_convert_email_verification() {
    SecurityMail mail =
        convert(
            EmailVerificationMailEvent.builder()
                .eventId(ID)
                .to(TO)
                .displayName(NAME)
                .verifyUrl("https://patra.example/verify-email#token=v")
                .linkExpiresAt(EXPIRES)
                .build());

    assertCommon(mail, SecurityMailKind.EMAIL_VERIFICATION);
    assertThat(mail.facts()).containsOnlyKeys("verifyUrl");
    assertThat(mail.facts())
        .containsEntry("verifyUrl", "https://patra.example/verify-email#token=v");
    assertThat(mail.linkExpiresAt()).isEqualTo(EXPIRES);
    assertThat(mail.context()).isNull();
  }

  /// 找回密码：重设链接与过期时刻。
  @Test
  void should_convert_password_reset() {
    SecurityMail mail =
        convert(
            PasswordResetMailEvent.builder()
                .eventId(ID)
                .to(TO)
                .displayName(NAME)
                .resetUrl("https://patra.example/reset-password#token=r")
                .linkExpiresAt(EXPIRES)
                .build());

    assertCommon(mail, SecurityMailKind.PASSWORD_RESET);
    assertThat(mail.facts()).containsOnlyKeys("resetUrl");
    assertThat(mail.linkExpiresAt()).isEqualTo(EXPIRES);
  }

  /// 密码已重设：只有操作上下文。
  @Test
  void should_convert_password_reset_done() {
    SecurityMail mail = convert(PasswordResetDoneMailEvent.of(ID, TO, NAME, CONTEXT));

    assertCommon(mail, SecurityMailKind.PASSWORD_RESET_DONE);
    assertThat(mail.facts()).isEmpty();
    assertContext(mail);
  }

  /// 密码已修改：只有操作上下文。
  @Test
  void should_convert_password_changed() {
    SecurityMail mail = convert(PasswordChangedMailEvent.of(ID, TO, NAME, CONTEXT));

    assertCommon(mail, SecurityMailKind.PASSWORD_CHANGED);
    assertThat(mail.facts()).isEmpty();
    assertContext(mail);
  }

  /// 改邮箱确认：确认链接与过期时刻，收件人是新邮箱（原样带过来）。
  @Test
  void should_convert_email_change_confirm() {
    SecurityMail mail =
        convert(
            EmailChangeConfirmMailEvent.builder()
                .eventId(ID)
                .to(TO)
                .displayName(NAME)
                .confirmUrl("https://patra.example/confirm-email-change#token=c")
                .linkExpiresAt(EXPIRES)
                .build());

    assertCommon(mail, SecurityMailKind.EMAIL_CHANGE_CONFIRM);
    assertThat(mail.facts())
        .containsOnlyKeys("confirmUrl")
        .containsEntry("confirmUrl", "https://patra.example/confirm-email-change#token=c");
    assertThat(mail.linkExpiresAt()).isEqualTo(EXPIRES);
  }

  /// 邮箱已更改：遮掩的新邮箱、撤销链接、安全设置链接进 `securityUrl`，带上下文与过期时刻。
  @Test
  void should_convert_email_changed_notice() {
    SecurityMail mail =
        convert(
            EmailChangedNoticeMailEvent.builder()
                .eventId(ID)
                .to(TO)
                .displayName(NAME)
                .newEmailMasked("n***@example.com")
                .revertUrl("https://patra.example/revert-email-change#token=x")
                .linkExpiresAt(EXPIRES)
                .securitySettingsUrl(SECURITY)
                .context(CONTEXT)
                .build());

    assertCommon(mail, SecurityMailKind.EMAIL_CHANGED_NOTICE);
    assertThat(mail.facts())
        .containsOnlyKeys("newEmailMasked", "revertUrl", "securityUrl")
        .containsEntry("newEmailMasked", "n***@example.com")
        .containsEntry("revertUrl", "https://patra.example/revert-email-change#token=x")
        .containsEntry("securityUrl", SECURITY);
    assertThat(mail.linkExpiresAt()).isEqualTo(EXPIRES);
    assertContext(mail);
  }

  /// 本邮箱已绑定：安全设置链接进 `securityUrl`。
  @Test
  void should_convert_email_changed_confirmation() {
    SecurityMail mail =
        convert(
            EmailChangedConfirmationMailEvent.builder()
                .eventId(ID)
                .to(TO)
                .displayName(NAME)
                .securitySettingsUrl(SECURITY)
                .context(CONTEXT)
                .build());

    assertCommon(mail, SecurityMailKind.EMAIL_CHANGED_CONFIRMATION);
    assertThat(mail.facts()).containsOnlyKeys("securityUrl").containsEntry("securityUrl", SECURITY);
    assertContext(mail);
  }

  /// 邮箱已改回：重设链接、过期时刻、上下文。
  @Test
  void should_convert_email_change_reverted() {
    SecurityMail mail =
        convert(
            EmailChangeRevertedMailEvent.builder()
                .eventId(ID)
                .to(TO)
                .displayName(NAME)
                .resetUrl("https://patra.example/reset-password#token=r")
                .linkExpiresAt(EXPIRES)
                .context(CONTEXT)
                .build());

    assertCommon(mail, SecurityMailKind.EMAIL_CHANGE_REVERTED);
    assertThat(mail.facts())
        .containsOnlyKeys("resetUrl")
        .containsEntry("resetUrl", "https://patra.example/reset-password#token=r");
    assertThat(mail.linkExpiresAt()).isEqualTo(EXPIRES);
    assertContext(mail);
  }

  /// 新设备登录：安全设置链接进 `securityUrl`，上下文原样。
  @Test
  void should_convert_new_device_login() {
    SecurityMail mail =
        convert(
            NewDeviceLoginMailEvent.builder()
                .eventId(ID)
                .to(TO)
                .displayName(NAME)
                .securitySettingsUrl(SECURITY)
                .context(CONTEXT)
                .build());

    assertCommon(mail, SecurityMailKind.NEW_DEVICE_LOGIN);
    assertThat(mail.facts()).containsOnlyKeys("securityUrl").containsEntry("securityUrl", SECURITY);
    assertContext(mail);
  }

  /// 注销已提交：到期时刻与上下文。
  @Test
  void should_convert_account_deletion_requested() {
    SecurityMail mail =
        convert(
            AccountDeletionRequestedMailEvent.builder()
                .eventId(ID)
                .to(TO)
                .displayName(NAME)
                .deletionDueAt(DUE)
                .context(CONTEXT)
                .build());

    assertCommon(mail, SecurityMailKind.ACCOUNT_DELETION_REQUESTED);
    assertThat(mail.facts()).containsOnlyKeys("deletionDueAt").containsEntry("deletionDueAt", DUE);
    assertContext(mail);
  }

  /// 已取消注销：只有上下文。
  @Test
  void should_convert_account_deletion_cancelled() {
    SecurityMail mail = convert(AccountDeletionCancelledMailEvent.of(ID, TO, NAME, CONTEXT));

    assertCommon(mail, SecurityMailKind.ACCOUNT_DELETION_CANCELLED);
    assertThat(mail.facts()).isEmpty();
    assertContext(mail);
  }

  /// 账号即将删除：到期时刻与剩余天数，没有上下文。
  @Test
  void should_convert_account_deletion_reminder() {
    SecurityMail mail =
        convert(
            AccountDeletionReminderMailEvent.builder()
                .eventId(ID)
                .to(TO)
                .displayName(NAME)
                .deletionDueAt(DUE)
                .daysLeft(3)
                .build());

    assertCommon(mail, SecurityMailKind.ACCOUNT_DELETION_REMINDER);
    assertThat(mail.facts())
        .containsOnlyKeys("deletionDueAt", "daysLeft")
        .containsEntry("deletionDueAt", DUE)
        .containsEntry("daysLeft", 3L);
    assertThat(mail.context()).isNull();
  }

  /// 账号已注销：用户名。
  @Test
  void should_convert_account_deletion_completed() {
    SecurityMail mail = convert(AccountDeletionCompletedMailEvent.of(ID, TO, NAME, "chen-yu"));

    assertCommon(mail, SecurityMailKind.ACCOUNT_DELETION_COMPLETED);
    assertThat(mail.facts()).containsOnlyKeys("username").containsEntry("username", "chen-yu");
  }

  /// 转换。
  ///
  /// @param event 事件
  /// @return 领域的安全邮件
  private static SecurityMail convert(
      dev.patra.identity.api.account.event.SecurityMailEvent event) {
    return SecurityMailEventConverter.toSecurityMail(event);
  }

  /// 13 种共有的部分。
  ///
  /// @param mail 转换结果
  /// @param kind 期望的类别
  private static void assertCommon(SecurityMail mail, SecurityMailKind kind) {
    assertThat(mail.kind()).isEqualTo(kind);
    assertThat(mail.eventId()).isEqualTo(ID);
    assertThat(mail.to()).isEqualTo(TO);
    assertThat(mail.displayName()).isEqualTo(NAME);
  }

  /// 操作上下文的四项原样带到领域。
  ///
  /// @param mail 转换结果
  private static void assertContext(SecurityMail mail) {
    assertThat(mail.context().occurredAt()).isEqualTo(CONTEXT.occurredAt());
    assertThat(mail.context().device()).isEqualTo(CONTEXT.device());
    assertThat(mail.context().ipAddress()).isEqualTo(CONTEXT.ipAddress());
    assertThat(mail.context().ipLocation()).isEqualTo(CONTEXT.ipLocation());
  }
}
