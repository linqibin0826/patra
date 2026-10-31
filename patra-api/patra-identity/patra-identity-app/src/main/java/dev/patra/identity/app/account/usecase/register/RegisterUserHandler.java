package dev.patra.identity.app.account.usecase.register;

import dev.patra.identity.app.account.usecase.login.AuthenticatedUser;
import dev.patra.identity.domain.account.exception.UserIdentifierTakenException;
import dev.patra.identity.domain.account.model.aggregate.EmailToken;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.aggregate.UserPasswordCredential;
import dev.patra.identity.domain.account.model.aggregate.UserProfile;
import dev.patra.identity.domain.account.model.enums.EmailTokenPurpose;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.PasswordHash;
import dev.patra.identity.domain.account.model.vo.PlainPassword;
import dev.patra.identity.domain.account.model.vo.RequestOrigin;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.domain.account.model.vo.mail.EmailVerificationMail;
import dev.patra.identity.domain.account.policy.MailLinkPolicy;
import dev.patra.identity.domain.account.policy.PasswordPolicy;
import dev.patra.identity.domain.account.policy.UsernamePolicy;
import dev.patra.identity.domain.account.port.hashing.PasswordHashingPort;
import dev.patra.identity.domain.account.port.mail.EmailDeliveryPort;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.account.port.repository.FormerUsernameRepository;
import dev.patra.identity.domain.account.port.repository.UserPasswordCredentialRepository;
import dev.patra.identity.domain.account.port.repository.UserProfileRepository;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.account.port.throttle.EmailResendThrottlePort;
import dev.patra.identity.domain.account.service.ClientOriginResolver;
import dev.patra.identity.domain.account.service.EmailTokenIssuer;
import dev.patra.identity.domain.account.service.IssuedEmailToken;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.session.model.vo.ClientOrigin;
import dev.patra.identity.domain.session.model.vo.IssuedUserSession;
import dev.patra.identity.domain.session.model.vo.LoginClient;
import dev.patra.identity.domain.session.model.vo.UserFieldViolations;
import dev.patra.identity.domain.session.service.SessionIssuer;
import dev.patra.tool.core.error.field.FieldViolation;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 注册前台用户，成功后直接建会话。
///
/// 顺序：五个字段一起校验（有错一次报全）→ 邮箱、用户名的占用一起查（别人保留期内的旧名也算占用，
/// 有错一次报全）→ 哈希 → 在一个事务里存用户、存空资料、存凭据、建会话。
/// 哈希放在事务外：事务开始时就会占住数据库连接，而哈希可能要排队几秒。
/// 建会话放在事务里：Redis 写失败整个注册回滚，用户重试不会撞 409。
/// 验证邮件的令牌也在事务里签发，邮件在同一事务里登记（与注册同成同败，提交后由通知服务发出，PAP-104）；
/// 事务提交后把首封计进重发限流（只计冷却和账号窗口，不判、不计 IP，失败只记 WARN）。
@Slf4j
@Component
@RequiredArgsConstructor
public class RegisterUserHandler implements CommandHandler<RegisterUserCommand, AuthenticatedUser> {

  private final UserRepository users;
  private final UserProfileRepository profiles;
  private final UserPasswordCredentialRepository credentials;
  private final FormerUsernameRepository formerUsernames;
  private final UserReadPort userReadPort;
  private final PasswordHashingPort passwordHashing;
  private final PasswordPolicy passwordPolicy;
  private final UsernamePolicy usernamePolicy;
  private final SessionIssuer sessionIssuer;
  private final ClientOriginResolver clientOriginResolver;
  private final EmailTokenIssuer emailTokenIssuer;
  private final MailLinkPolicy links;
  private final EmailDeliveryPort emailDelivery;
  private final EmailResendThrottlePort emailResendThrottle;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 注册并登录。
  ///
  /// @param command 命令
  /// @return 会话令牌和新用户的账号信息
  @Override
  public AuthenticatedUser handle(RegisterUserCommand command) {
    List<FieldViolation> violations = new ArrayList<>();
    EmailAddress.validate(command.email()).ifPresent(violations::add);
    usernamePolicy.validate(command.username()).ifPresent(violations::add);
    passwordPolicy
        .validate(command.password(), UserFieldViolations.PASSWORD)
        .ifPresent(violations::add);
    violations.addAll(LoginClient.validate(command.clientType(), command.deviceId()));
    if (!violations.isEmpty()) {
      throw new InvalidUserFieldsException(violations);
    }
    EmailAddress email = EmailAddress.of(command.email());
    Username username = Username.of(command.username());
    LoginClient client = LoginClient.of(command.clientType(), command.deviceId());
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
    PasswordHash hash = passwordHashing.hash(PlainPassword.of(command.password()));
    // 解析 UA 与属地是纯计算：字段与唯一性校验通过之后、事务之外
    ClientOrigin origin =
        clientOriginResolver.resolve(RequestOrigin.of(command.clientIp(), command.userAgent()));
    Registration registration =
        Objects.requireNonNull(
            transactions.execute(
                status -> {
                  User saved = users.save(User.register(email, username));
                  profiles.save(UserProfile.create(saved.getId()));
                  credentials.save(UserPasswordCredential.create(saved.getId(), hash));
                  IssuedUserSession session = sessionIssuer.issue(saved.getId(), client, origin);
                  UserAccountReadModel account =
                      userReadPort
                          .findAccount(saved.getId())
                          .orElseThrow(
                              () -> new IllegalStateException("刚保存的用户读不回来: " + saved.getId()));
                  IssuedEmailToken issued =
                      emailTokenIssuer.issue(
                          EmailTokenPurpose.EMAIL_VERIFY, saved.getId(), email, command.clientIp());
                  emailDelivery.send(
                      EmailVerificationMail.builder()
                          .to(email)
                          .displayName(account.displayName())
                          .verifyUrl(links.verifyEmailUrl(issued.secret()))
                          .linkExpiresAt(issued.token().getExpiresAt())
                          .build());
                  return new Registration(saved, session, account, issued.token());
                }));
    long userId = registration.user().getId();
    markSentQuietly(userId, now);
    log.info(
        "前台用户已注册并登录，验证邮件已登记: userId={}, username={}, tokenId={}",
        userId,
        registration.user().getUsername().value(),
        registration.token().getId());
    return AuthenticatedUser.of(registration.session().token(), registration.account());
  }

  /// 把注册的首封邮件计进重发限流。失败只记 WARN：注册不能因为计数失败而失败。
  ///
  /// @param userId 用户 ID
  /// @param now 当前时间
  private void markSentQuietly(long userId, Instant now) {
    try {
      emailResendThrottle.markSent(userId, now);
    } catch (RuntimeException e) {
      log.warn("注册的首封验证邮件计入重发限流失败: userId={}", userId, e);
    }
  }

  /// 事务里一起产出的用户、会话、账号信息和签发好的验证令牌。
  ///
  /// @param user 新用户
  /// @param session 新会话
  /// @param account 账号信息
  /// @param token 签发好的验证令牌
  private record Registration(
      User user, IssuedUserSession session, UserAccountReadModel account, EmailToken token) {}
}
