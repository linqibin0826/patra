package dev.patra.notification.adapter.mail.mq;

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
import dev.patra.identity.api.account.event.PasswordChangedMailEvent;
import dev.patra.identity.api.account.event.PasswordResetDoneMailEvent;
import dev.patra.identity.api.account.event.PasswordResetMailEvent;
import dev.patra.identity.api.account.event.SecurityMailEvent;
import dev.patra.notification.domain.mail.model.enums.SecurityMailKind;
import dev.patra.notification.domain.mail.model.vo.OperationContext;
import dev.patra.notification.domain.mail.model.vo.SecurityMail;
import java.util.Map;

/// identity 的安全邮件事件 → 领域的 {@link SecurityMail}。
///
/// 对 sealed 的 13 种穷举，identity 加一种而这里没跟上时编译不过。`facts` 的键与模板变量同名；
/// 事件的 `securitySettingsUrl` 进模板变量 `securityUrl`。
final class SecurityMailEventConverter {

  /// 不实例化。
  private SecurityMailEventConverter() {}

  /// 转换。
  ///
  /// @param event identity 的事件
  /// @return 领域的安全邮件
  static SecurityMail toSecurityMail(SecurityMailEvent event) {
    return switch (event) {
      case EmailVerificationMailEvent e ->
          base(e, SecurityMailKind.EMAIL_VERIFICATION)
              .facts(Map.of("verifyUrl", e.verifyUrl()))
              .linkExpiresAt(e.linkExpiresAt())
              .build();
      case PasswordResetMailEvent e ->
          base(e, SecurityMailKind.PASSWORD_RESET)
              .facts(Map.of("resetUrl", e.resetUrl()))
              .linkExpiresAt(e.linkExpiresAt())
              .build();
      case PasswordResetDoneMailEvent e ->
          base(e, SecurityMailKind.PASSWORD_RESET_DONE).context(context(e.context())).build();
      case PasswordChangedMailEvent e ->
          base(e, SecurityMailKind.PASSWORD_CHANGED).context(context(e.context())).build();
      case EmailChangeConfirmMailEvent e ->
          base(e, SecurityMailKind.EMAIL_CHANGE_CONFIRM)
              .facts(Map.of("confirmUrl", e.confirmUrl()))
              .linkExpiresAt(e.linkExpiresAt())
              .build();
      case EmailChangedNoticeMailEvent e ->
          base(e, SecurityMailKind.EMAIL_CHANGED_NOTICE)
              .facts(
                  Map.of(
                      "newEmailMasked", e.newEmailMasked(),
                      "revertUrl", e.revertUrl(),
                      "securityUrl", e.securitySettingsUrl()))
              .linkExpiresAt(e.linkExpiresAt())
              .context(context(e.context()))
              .build();
      case EmailChangedConfirmationMailEvent e ->
          base(e, SecurityMailKind.EMAIL_CHANGED_CONFIRMATION)
              .facts(Map.of("securityUrl", e.securitySettingsUrl()))
              .context(context(e.context()))
              .build();
      case EmailChangeRevertedMailEvent e ->
          base(e, SecurityMailKind.EMAIL_CHANGE_REVERTED)
              .facts(Map.of("resetUrl", e.resetUrl()))
              .linkExpiresAt(e.linkExpiresAt())
              .context(context(e.context()))
              .build();
      case NewDeviceLoginMailEvent e ->
          base(e, SecurityMailKind.NEW_DEVICE_LOGIN)
              .facts(Map.of("securityUrl", e.securitySettingsUrl()))
              .context(context(e.context()))
              .build();
      case AccountDeletionRequestedMailEvent e ->
          base(e, SecurityMailKind.ACCOUNT_DELETION_REQUESTED)
              .facts(Map.of("deletionDueAt", e.deletionDueAt()))
              .context(context(e.context()))
              .build();
      case AccountDeletionCancelledMailEvent e ->
          base(e, SecurityMailKind.ACCOUNT_DELETION_CANCELLED)
              .context(context(e.context()))
              .build();
      case AccountDeletionReminderMailEvent e ->
          base(e, SecurityMailKind.ACCOUNT_DELETION_REMINDER)
              .facts(Map.of("deletionDueAt", e.deletionDueAt(), "daysLeft", e.daysLeft()))
              .build();
      case AccountDeletionCompletedMailEvent e ->
          base(e, SecurityMailKind.ACCOUNT_DELETION_COMPLETED)
              .facts(Map.of("username", e.username()))
              .build();
    };
  }

  /// 13 种共有的部分：事件 ID、类别、收件地址、昵称。
  ///
  /// @param event 事件
  /// @param kind 类别
  /// @return 填好共有部分的构建器
  private static SecurityMail.SecurityMailBuilder base(
      SecurityMailEvent event, SecurityMailKind kind) {
    return SecurityMail.builder()
        .eventId(event.eventId())
        .kind(kind)
        .to(event.to())
        .displayName(event.displayName());
  }

  /// 契约的操作上下文 → 领域的操作上下文（两边同名，契约这一侧用全类名区分）。
  ///
  /// @param source 事件里的操作上下文
  /// @return 领域的操作上下文
  private static OperationContext context(
      dev.patra.identity.api.account.event.OperationContext source) {
    return OperationContext.of(
        source.occurredAt(), source.device(), source.ipAddress(), source.ipLocation());
  }
}
