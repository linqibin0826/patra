package dev.patra.identity.app.account.usecase.oauth;

import dev.patra.identity.app.account.service.PendingRegistrationCodes;
import dev.patra.identity.app.account.service.PendingRegistrationCodes.IssuedCode;
import dev.patra.identity.app.account.usecase.query.PendingRegistrationQueryService;
import dev.patra.identity.domain.account.exception.RegistrationCodeLimitedException;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.enums.RegistrationCodePurpose;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.PendingRegistration;
import dev.patra.identity.domain.account.model.vo.mail.LoginMethodLinkCodeMail;
import dev.patra.identity.domain.account.port.mail.EmailDeliveryPort;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.account.port.throttle.RegistrationCodeThrottlePort;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 邮箱撞车时就地绑定：发验证码（v0.19 决策 D 的补充）。
///
/// 不能比「已注册」多泄露：邮箱没注册、账号邮箱未验证、已封禁时，响应、验证码记录和后面的错误次数都和有资格时一样，
/// 只是不发信。所以码总是写：没资格时写的是谁也收不到的码。
///
/// 顺序：凭证（422）→ 邮箱（422）→ 限流（429，排在判断资格之前：配额和等待时间只看邮箱，不因账号状态而不同；含同邮箱
/// 每天的上限，6 位数字猜中就能登录别人的账号）→ 判断资格 → 写验证码（Redis）→
/// 有资格才在短事务里登记邮件（`EmailDeliveryPort` 要求在事务里调）。
@Slf4j
@Component
@RequiredArgsConstructor
public class SendProviderLinkCodeHandler
    implements CommandHandler<SendProviderLinkCodeCommand, RegistrationCodeSent> {

  private final PendingRegistrationQueryService pendingQueries;
  private final PendingRegistrationCodes codes;
  private final UserRepository users;
  private final UserReadPort userReadPort;
  private final RegistrationCodeThrottlePort throttle;
  private final EmailDeliveryPort emailDelivery;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 发验证码。
  ///
  /// @param command 命令
  /// @return 邮箱和时间
  @Override
  public RegistrationCodeSent handle(SendProviderLinkCodeCommand command) {
    PendingRegistration registration = pendingQueries.require(command.ticket());
    EmailAddress.validate(command.email())
        .ifPresent(
            violation -> {
              throw new InvalidUserFieldsException(List.of(violation));
            });
    EmailAddress email = EmailAddress.of(command.email());
    Instant now = clock.instant();
    throttle
        .acquireForLink(email, command.clientIp(), now)
        .ifPresent(
            wait -> {
              throw new RegistrationCodeLimitedException(wait);
            });
    Optional<User> target = users.findByEmail(email).filter(User::canReceiveProviderLink);
    IssuedCode issued =
        codes.issue(
            command.ticket(), registration, RegistrationCodePurpose.LINK, null, email.value(), now);
    target.ifPresent(user -> sendMail(user, email, registration, issued));
    log.info("就地绑定验证码已登记: provider={}, mailSent={}", registration.provider(), target.isPresent());
    return RegistrationCodeSent.of(
        email.value(), issued.pendingCode().codeExpiresAt(), issued.pendingCode().nextResendAt());
  }

  /// 在短事务里登记验证码邮件。
  ///
  /// @param user 要绑到的账号
  /// @param email 收件邮箱
  /// @param registration 待补填记录
  /// @param issued 验证码
  private void sendMail(
      User user, EmailAddress email, PendingRegistration registration, IssuedCode issued) {
    UserAccountReadModel account =
        userReadPort
            .findAccount(user.getId())
            .orElseThrow(() -> new IllegalStateException("刚查到的用户读不回来: " + user.getId()));
    transactions.executeWithoutResult(
        status ->
            emailDelivery.send(
                LoginMethodLinkCodeMail.builder()
                    .to(email)
                    .displayName(account.displayName())
                    .username(account.username())
                    .code(issued.code().value())
                    .codeExpiresAt(issued.pendingCode().codeExpiresAt())
                    .providerName(registration.provider().displayName())
                    .accountLabel(registration.accountLabel())
                    .build()));
  }
}
