package dev.patra.identity.app.usecase.register;

import dev.patra.identity.app.usecase.auth.AuthenticatedUser;
import dev.patra.identity.domain.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.exception.UserIdentifierTakenException;
import dev.patra.identity.domain.model.aggregate.User;
import dev.patra.identity.domain.model.aggregate.UserPasswordCredential;
import dev.patra.identity.domain.model.aggregate.UserProfile;
import dev.patra.identity.domain.model.read.UserAccountReadModel;
import dev.patra.identity.domain.model.vo.EmailAddress;
import dev.patra.identity.domain.model.vo.LoginClient;
import dev.patra.identity.domain.model.vo.PasswordHash;
import dev.patra.identity.domain.model.vo.PlainPassword;
import dev.patra.identity.domain.model.vo.UserFieldViolations;
import dev.patra.identity.domain.model.vo.Username;
import dev.patra.identity.domain.policy.PasswordPolicy;
import dev.patra.identity.domain.policy.UsernamePolicy;
import dev.patra.identity.domain.port.hashing.PasswordHashingPort;
import dev.patra.identity.domain.port.read.UserReadPort;
import dev.patra.identity.domain.port.repository.FormerUsernameRepository;
import dev.patra.identity.domain.port.repository.UserPasswordCredentialRepository;
import dev.patra.identity.domain.port.repository.UserProfileRepository;
import dev.patra.identity.domain.port.repository.UserRepository;
import dev.patra.identity.domain.port.session.IssuedUserSession;
import dev.patra.identity.domain.service.SessionIssuer;
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
    passwordPolicy.validateForRegistration(command.password()).ifPresent(violations::add);
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
    Registration registration =
        Objects.requireNonNull(
            transactions.execute(
                status -> {
                  User saved = users.save(User.register(email, username));
                  profiles.save(UserProfile.create(saved.getId()));
                  credentials.save(UserPasswordCredential.create(saved.getId(), hash));
                  IssuedUserSession session = sessionIssuer.issue(saved.getId(), client);
                  UserAccountReadModel account =
                      userReadPort
                          .findAccount(saved.getId())
                          .orElseThrow(
                              () -> new IllegalStateException("刚保存的用户读不回来: " + saved.getId()));
                  return new Registration(saved, session, account);
                }));
    log.info(
        "前台用户已注册并登录: userId={}, username={}",
        registration.user().getId(),
        registration.user().getUsername().value());
    return AuthenticatedUser.of(registration.session().token(), registration.account());
  }

  /// 事务里一起产出的用户、会话和账号信息。
  ///
  /// @param user 新用户
  /// @param session 新会话
  /// @param account 账号信息
  private record Registration(User user, IssuedUserSession session, UserAccountReadModel account) {}
}
