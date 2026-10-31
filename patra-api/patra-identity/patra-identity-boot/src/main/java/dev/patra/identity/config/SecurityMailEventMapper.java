package dev.patra.identity.config;

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
import dev.patra.identity.domain.account.model.vo.mail.AccountDeletionCancelledMail;
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
import dev.patra.identity.domain.account.model.vo.mail.PasswordChangedMail;
import dev.patra.identity.domain.account.model.vo.mail.PasswordResetDoneMail;
import dev.patra.identity.domain.account.model.vo.mail.PasswordResetMail;
import dev.patra.identity.domain.account.policy.MailLinkPolicy;
import dev.patra.identity.domain.session.model.vo.SecurityNotice;
import java.util.Objects;

/// 把登记表里的安全邮件（领域事件）映射成 `patra-identity-api` 里的集成事件，并给出路由键（PAP-104）。
///
/// 每次外送（含补投）都会重新映射，所以事件 ID 只能原样复制，不能在这里生成。安全设置页没有令牌，外送时按当前配置
/// 现拼；带令牌的链接只能在签发时拼（令牌原文不落库），所以由领域记录带着。「不是我怎么办」与主题是文案，归通知服务，
/// 不进集成事件。
public final class SecurityMailEventMapper {

  private final MailLinkPolicy links;

  /// 创建映射器。
  ///
  /// @param links 邮件链接规则，取安全设置页的地址
  public SecurityMailEventMapper(MailLinkPolicy links) {
    this.links = Objects.requireNonNull(links, "links 不能为 null");
  }

  /// 映射成集成事件。
  ///
  /// @param mail 登记表里的安全邮件
  /// @return 集成事件
  /// @throws IllegalArgumentException 不认识的邮件类型
  public SecurityMailEvent toIntegrationEvent(OutgoingMail mail) {
    return switch (mail) {
      case EmailVerificationMail m ->
          EmailVerificationMailEvent.builder()
              .eventId(m.eventId())
              .to(m.to().value())
              .displayName(m.displayName())
              .verifyUrl(m.verifyUrl())
              .linkExpiresAt(m.linkExpiresAt())
              .build();
      case PasswordResetMail m ->
          PasswordResetMailEvent.builder()
              .eventId(m.eventId())
              .to(m.to().value())
              .displayName(m.displayName())
              .resetUrl(m.resetUrl())
              .linkExpiresAt(m.linkExpiresAt())
              .build();
      case PasswordResetDoneMail m ->
          PasswordResetDoneMailEvent.of(
              m.eventId(), m.to().value(), m.displayName(), context(m.notice()));
      case PasswordChangedMail m ->
          PasswordChangedMailEvent.of(
              m.eventId(), m.to().value(), m.displayName(), context(m.notice()));
      case EmailChangeConfirmMail m ->
          EmailChangeConfirmMailEvent.builder()
              .eventId(m.eventId())
              .to(m.to().value())
              .displayName(m.displayName())
              .confirmUrl(m.confirmUrl())
              .linkExpiresAt(m.linkExpiresAt())
              .build();
      case EmailChangedNoticeMail m ->
          EmailChangedNoticeMailEvent.builder()
              .eventId(m.eventId())
              .to(m.to().value())
              .displayName(m.displayName())
              .newEmailMasked(m.newEmailMasked())
              .revertUrl(m.revertUrl())
              .linkExpiresAt(m.linkExpiresAt())
              .securitySettingsUrl(links.securitySettingsUrl())
              .context(context(m.notice()))
              .build();
      case EmailChangedConfirmationMail m ->
          EmailChangedConfirmationMailEvent.builder()
              .eventId(m.eventId())
              .to(m.to().value())
              .displayName(m.displayName())
              .securitySettingsUrl(links.securitySettingsUrl())
              .context(context(m.notice()))
              .build();
      case EmailChangeRevertedMail m ->
          EmailChangeRevertedMailEvent.builder()
              .eventId(m.eventId())
              .to(m.to().value())
              .displayName(m.displayName())
              .resetUrl(m.resetUrl())
              .linkExpiresAt(m.linkExpiresAt())
              .context(context(m.notice()))
              .build();
      case NewDeviceLoginMail m ->
          NewDeviceLoginMailEvent.builder()
              .eventId(m.eventId())
              .to(m.to().value())
              .displayName(m.displayName())
              .securitySettingsUrl(links.securitySettingsUrl())
              .context(context(m.notice()))
              .build();
      case AccountDeletionRequestedMail m ->
          AccountDeletionRequestedMailEvent.builder()
              .eventId(m.eventId())
              .to(m.to().value())
              .displayName(m.displayName())
              .deletionDueAt(m.deletionDueAt())
              .context(context(m.notice()))
              .build();
      case AccountDeletionCancelledMail m ->
          AccountDeletionCancelledMailEvent.of(
              m.eventId(), m.to().value(), m.displayName(), context(m.notice()));
      case AccountDeletionReminderMail m ->
          AccountDeletionReminderMailEvent.builder()
              .eventId(m.eventId())
              .to(m.to().value())
              .displayName(m.displayName())
              .deletionDueAt(m.deletionDueAt())
              .daysLeft(m.daysLeft())
              .build();
      case AccountDeletionCompletedMail m ->
          AccountDeletionCompletedMailEvent.of(
              m.eventId(), m.to().value(), m.displayName(), m.username());
      default -> throw new IllegalArgumentException("不认识的安全邮件类型: " + mail.getClass().getName());
    };
  }

  /// 集成事件的路由键。对 sealed 接口穷举，新增事件种类漏写时编译不过。
  ///
  /// @param event 集成事件
  /// @return 路由键
  public static String routingKeyOf(SecurityMailEvent event) {
    return switch (event) {
      case EmailVerificationMailEvent _ -> EmailVerificationMailEvent.ROUTING_KEY;
      case PasswordResetMailEvent _ -> PasswordResetMailEvent.ROUTING_KEY;
      case PasswordResetDoneMailEvent _ -> PasswordResetDoneMailEvent.ROUTING_KEY;
      case PasswordChangedMailEvent _ -> PasswordChangedMailEvent.ROUTING_KEY;
      case EmailChangeConfirmMailEvent _ -> EmailChangeConfirmMailEvent.ROUTING_KEY;
      case EmailChangedNoticeMailEvent _ -> EmailChangedNoticeMailEvent.ROUTING_KEY;
      case EmailChangedConfirmationMailEvent _ -> EmailChangedConfirmationMailEvent.ROUTING_KEY;
      case EmailChangeRevertedMailEvent _ -> EmailChangeRevertedMailEvent.ROUTING_KEY;
      case NewDeviceLoginMailEvent _ -> NewDeviceLoginMailEvent.ROUTING_KEY;
      case AccountDeletionRequestedMailEvent _ -> AccountDeletionRequestedMailEvent.ROUTING_KEY;
      case AccountDeletionCancelledMailEvent _ -> AccountDeletionCancelledMailEvent.ROUTING_KEY;
      case AccountDeletionReminderMailEvent _ -> AccountDeletionReminderMailEvent.ROUTING_KEY;
      case AccountDeletionCompletedMailEvent _ -> AccountDeletionCompletedMailEvent.ROUTING_KEY;
    };
  }

  /// 安全通知上下文：只取事实，不取「不是我怎么办」文案。
  ///
  /// @param notice 领域的安全通知
  /// @return 集成事件里的操作上下文
  private static OperationContext context(SecurityNotice notice) {
    return OperationContext.of(
        notice.occurredAt(), notice.device(), notice.ipAddress(), notice.ipLocation());
  }
}
