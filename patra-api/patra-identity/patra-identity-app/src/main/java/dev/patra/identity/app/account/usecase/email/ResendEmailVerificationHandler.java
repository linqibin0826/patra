package dev.patra.identity.app.account.usecase.email;

import dev.patra.identity.domain.account.exception.EmailAlreadyVerifiedException;
import dev.patra.identity.domain.account.exception.EmailResendLimitedException;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.enums.EmailTokenPurpose;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.mail.EmailVerificationMail;
import dev.patra.identity.domain.account.policy.EmailResendPolicy;
import dev.patra.identity.domain.account.policy.MailLinkPolicy;
import dev.patra.identity.domain.account.port.mail.EmailDeliveryPort;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.account.port.throttle.EmailResendThrottlePort;
import dev.patra.identity.domain.account.service.EmailTokenIssuer;
import dev.patra.identity.domain.account.service.IssuedEmailToken;
import dev.patra.tool.core.security.AuthenticationRequiredException;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 当前用户重发验证邮件。
///
/// 顺序：不加锁预判状态（已验证 409、注销中 409、封禁 / 不存在 401，乱点的人不消耗配额）→ 限流
/// （放行即已计数，之后失败不退还）→ 事务里加行锁重读、重判、作废旧令牌并签发新的 → 事务提交后异步发信。
/// 行锁让同一用户的两次并发重发串行，第二次在锁后重判。
@Slf4j
@Component
@RequiredArgsConstructor
public class ResendEmailVerificationHandler
    implements CommandHandler<ResendEmailVerificationCommand, EmailVerificationResent> {

  private final CurrentUserPort currentUserPort;
  private final UserRepository users;
  private final UserReadPort userReadPort;
  private final EmailResendThrottlePort emailResendThrottle;
  private final EmailResendPolicy resendPolicy;
  private final EmailTokenIssuer emailTokenIssuer;
  private final MailLinkPolicy links;
  private final EmailDeliveryPort emailDelivery;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 重发。
  ///
  /// @param command 命令
  /// @return 收件邮箱和下次可重发时间
  @Override
  public EmailVerificationResent handle(ResendEmailVerificationCommand command) {
    CurrentUser current = currentUserPort.require();
    long userId = current.userId();
    requireResendable(users.findById(userId).orElseThrow(AuthenticationRequiredException::new));
    Instant now = clock.instant();
    emailResendThrottle
        .acquire(userId, command.clientIp(), now)
        .ifPresent(
            wait -> {
              throw new EmailResendLimitedException(wait);
            });
    Prepared prepared =
        Objects.requireNonNull(
            transactions.execute(
                status -> {
                  User locked =
                      users
                          .findByIdForUpdate(userId)
                          .orElseThrow(AuthenticationRequiredException::new);
                  requireResendable(locked);
                  EmailAddress email =
                      locked.getEmail().orElseThrow(AuthenticationRequiredException::new);
                  UserAccountReadModel account =
                      userReadPort
                          .findAccount(userId)
                          .orElseThrow(AuthenticationRequiredException::new);
                  IssuedEmailToken issued =
                      emailTokenIssuer.issue(
                          EmailTokenPurpose.EMAIL_VERIFY, userId, email, command.clientIp());
                  return new Prepared(
                      issued,
                      EmailVerificationMail.of(
                          email,
                          account.displayName(),
                          links.verifyEmailUrl(issued.secret()),
                          issued.token().getExpiresAt()));
                }));
    emailDelivery.send(prepared.mail());
    log.info("验证邮件已重发排队: userId={}, tokenId={}", userId, prepared.issued().token().getId());
    return EmailVerificationResent.of(prepared.mail().to().value(), resendPolicy.nextResendAt(now));
  }

  /// 事务里签发好的令牌和邮件。
  ///
  /// @param issued 已保存的令牌与原文
  /// @param mail 要发的邮件
  private record Prepared(IssuedEmailToken issued, EmailVerificationMail mail) {}

  /// 能不能重发：正常状态且邮箱未验证。
  ///
  /// @param user 用户
  /// @throws EmailAlreadyVerifiedException 已验证
  private static void requireResendable(User user) {
    user.requireActive();
    if (user.isEmailVerified()) {
      throw new EmailAlreadyVerifiedException();
    }
  }
}
