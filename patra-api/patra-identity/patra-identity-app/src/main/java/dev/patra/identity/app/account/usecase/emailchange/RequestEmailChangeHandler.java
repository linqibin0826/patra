package dev.patra.identity.app.account.usecase.emailchange;

import dev.patra.identity.domain.account.exception.EmailChangeLimitedException;
import dev.patra.identity.domain.account.exception.EmailChangePendingException;
import dev.patra.identity.domain.account.exception.UserIdentifierTakenException;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.enums.EmailTokenPurpose;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.mail.EmailChangeConfirmMail;
import dev.patra.identity.domain.account.policy.MailLinkPolicy;
import dev.patra.identity.domain.account.port.mail.EmailDeliveryPort;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.account.port.repository.EmailTokenRepository;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.account.port.throttle.EmailChangeThrottlePort;
import dev.patra.identity.domain.account.service.EmailTokenIssuer;
import dev.patra.identity.domain.account.service.IssuedEmailToken;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.session.model.vo.UserFieldViolations;
import dev.patra.identity.domain.session.service.ReauthenticationGuard;
import dev.patra.tool.core.security.AuthenticationRequiredException;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 改邮箱的申请：向新邮箱发 1 小时有效的确认链接。
///
/// 顺序（错误码从便宜到贵）：字段（422，含与当前相同）→ 状态（409 / 401）→ 再认证（403）→
/// 已有待确认（409 `EMAIL_CHANGE_PENDING`）→ 新邮箱被别人使用（409 `email` / `ALREADY_REGISTERED`，
/// 和注册一致，不消耗配额）→ 限流（429）→ 事务里锁用户行、重判、签发 `EMAIL_CHANGE_CONFIRM` → 事务后发信。
/// 「待确认」就是一条未过期的确认令牌，不加表列。
@Slf4j
@Component
@RequiredArgsConstructor
public class RequestEmailChangeHandler
    implements CommandHandler<RequestEmailChangeCommand, EmailChangeRequested> {

  private final CurrentUserPort currentUserPort;
  private final UserRepository users;
  private final UserReadPort userReadPort;
  private final ReauthenticationGuard reauthenticationGuard;
  private final EmailTokenRepository tokens;
  private final EmailChangeThrottlePort emailChangeThrottle;
  private final EmailTokenIssuer emailTokenIssuer;
  private final MailLinkPolicy links;
  private final EmailDeliveryPort emailDelivery;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 申请。
  ///
  /// @param command 命令
  /// @return 新邮箱和确认链接的过期时间
  @Override
  public EmailChangeRequested handle(RequestEmailChangeCommand command) {
    CurrentUser current = currentUserPort.require();
    EmailAddress.validate(command.newEmail(), UserFieldViolations.NEW_EMAIL)
        .ifPresent(
            violation -> {
              throw new InvalidUserFieldsException(List.of(violation));
            });
    EmailAddress newEmail = EmailAddress.of(command.newEmail());
    long userId = current.userId();
    User user = users.findById(userId).orElseThrow(AuthenticationRequiredException::new);
    if (user.getEmail().map(newEmail::equals).orElse(false)) {
      throw new InvalidUserFieldsException(List.of(UserFieldViolations.newEmailUnchanged()));
    }
    user.requireActive();
    reauthenticationGuard.requireRecent(current);
    Instant now = clock.instant();
    requireNoPendingChange(userId, now);
    if (users.existsByEmail(newEmail)) {
      throw new UserIdentifierTakenException(List.of(UserFieldViolations.emailAlreadyRegistered()));
    }
    emailChangeThrottle
        .acquire(userId, now)
        .ifPresent(
            wait -> {
              throw new EmailChangeLimitedException(wait);
            });
    Prepared prepared =
        Objects.requireNonNull(
            transactions.execute(
                status -> {
                  User locked =
                      users
                          .findByIdForUpdate(userId)
                          .orElseThrow(AuthenticationRequiredException::new);
                  locked.requireActive();
                  requireNoPendingChange(userId, now);
                  UserAccountReadModel account =
                      userReadPort
                          .findAccount(userId)
                          .orElseThrow(AuthenticationRequiredException::new);
                  IssuedEmailToken issued =
                      emailTokenIssuer.issue(
                          EmailTokenPurpose.EMAIL_CHANGE_CONFIRM,
                          userId,
                          newEmail,
                          command.clientIp());
                  return new Prepared(
                      issued,
                      EmailChangeConfirmMail.of(
                          newEmail,
                          account.displayName(),
                          links.confirmEmailChangeUrl(issued.secret())));
                }));
    emailDelivery.send(prepared.mail());
    log.info("改邮箱确认邮件已排队: userId={}, tokenId={}", userId, prepared.issued().token().getId());
    return EmailChangeRequested.of(newEmail.value(), prepared.issued().token().getExpiresAt());
  }

  /// 已有未过期的确认令牌就拒绝。
  ///
  /// @param userId 用户 ID
  /// @param now 当前时间
  /// @throws EmailChangePendingException 已有待确认
  private void requireNoPendingChange(long userId, Instant now) {
    if (tokens.findActive(userId, EmailTokenPurpose.EMAIL_CHANGE_CONFIRM, now).isPresent()) {
      throw new EmailChangePendingException();
    }
  }

  /// 事务里签发好的令牌和邮件。
  ///
  /// @param issued 已保存的令牌与原文
  /// @param mail 要发的邮件
  private record Prepared(IssuedEmailToken issued, EmailChangeConfirmMail mail) {}
}
