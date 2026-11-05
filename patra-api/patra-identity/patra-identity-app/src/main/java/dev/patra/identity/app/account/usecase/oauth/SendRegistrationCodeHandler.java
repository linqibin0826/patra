package dev.patra.identity.app.account.usecase.oauth;

import dev.patra.identity.app.account.service.PendingRegistrationCodes;
import dev.patra.identity.app.account.service.PendingRegistrationCodes.IssuedCode;
import dev.patra.identity.app.account.usecase.query.PendingRegistrationQueryService;
import dev.patra.identity.domain.account.exception.RegistrationCodeLimitedException;
import dev.patra.identity.domain.account.exception.UserIdentifierTakenException;
import dev.patra.identity.domain.account.model.enums.RegistrationCodePurpose;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.PendingRegistration;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.domain.account.model.vo.mail.RegistrationCodeMail;
import dev.patra.identity.domain.account.policy.UsernamePolicy;
import dev.patra.identity.domain.account.port.mail.EmailDeliveryPort;
import dev.patra.identity.domain.account.port.repository.FormerUsernameRepository;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.account.port.throttle.RegistrationCodeThrottlePort;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.session.model.vo.UserFieldViolations;
import dev.patra.tool.core.error.field.FieldViolation;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 第三方首次注册：校验用户名和邮箱，发验证码。这一步不建账号（v0.19 决策 D）。
///
/// 顺序（错误从便宜到贵）：凭证（422）→ 字段（422，一次报全）→ 占用（409，一次报全；邮箱撞车不发码，门户提示先用原方式
/// 登录再绑定）→ 限流（429，查占用之后：填错的不消耗配额）→ 写验证码（Redis）→ 短事务里登记邮件（`EmailDeliveryPort`
/// 要求在事务里调）。邮件登记失败时验证码留在 Redis 也无害：用户重试会覆盖。
@Slf4j
@Component
@RequiredArgsConstructor
public class SendRegistrationCodeHandler
    implements CommandHandler<SendRegistrationCodeCommand, RegistrationCodeSent> {

  private final PendingRegistrationQueryService pendingQueries;
  private final PendingRegistrationCodes codes;
  private final UserRepository users;
  private final FormerUsernameRepository formerUsernames;
  private final UsernamePolicy usernamePolicy;
  private final RegistrationCodeThrottlePort throttle;
  private final EmailDeliveryPort emailDelivery;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 发验证码。
  ///
  /// @param command 命令
  /// @return 邮箱和时间
  @Override
  public RegistrationCodeSent handle(SendRegistrationCodeCommand command) {
    PendingRegistration registration = pendingQueries.require(command.ticket());
    List<FieldViolation> violations = new ArrayList<>();
    EmailAddress.validate(command.email()).ifPresent(violations::add);
    usernamePolicy.validate(command.username()).ifPresent(violations::add);
    if (!violations.isEmpty()) {
      throw new InvalidUserFieldsException(violations);
    }
    EmailAddress email = EmailAddress.of(command.email());
    Username username = Username.of(command.username());
    Instant now = clock.instant();
    List<FieldViolation> taken = new ArrayList<>();
    if (users.existsByEmail(email)) {
      taken.add(UserFieldViolations.emailAlreadyRegistered());
    }
    if (users.existsByUsername(username) || formerUsernames.existsReserved(username, now)) {
      taken.add(UserFieldViolations.usernameAlreadyTaken());
    }
    if (!taken.isEmpty()) {
      throw new UserIdentifierTakenException(taken);
    }
    throttle
        .acquire(email, command.clientIp(), now)
        .ifPresent(
            wait -> {
              throw new RegistrationCodeLimitedException(wait);
            });
    IssuedCode issued =
        codes.issue(
            command.ticket(),
            registration,
            RegistrationCodePurpose.REGISTER,
            username.value(),
            email.value(),
            now);
    transactions.executeWithoutResult(
        status ->
            emailDelivery.send(
                RegistrationCodeMail.builder()
                    .to(email)
                    .displayName(username.value())
                    .code(issued.code().value())
                    .codeExpiresAt(issued.pendingCode().codeExpiresAt())
                    .build()));
    log.info("第三方注册验证码已登记: provider={}", registration.provider());
    return RegistrationCodeSent.of(
        email.value(), issued.pendingCode().codeExpiresAt(), issued.pendingCode().nextResendAt());
  }
}
