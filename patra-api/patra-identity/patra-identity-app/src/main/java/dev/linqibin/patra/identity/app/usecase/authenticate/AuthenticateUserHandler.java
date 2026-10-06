package dev.linqibin.patra.identity.app.usecase.authenticate;

import dev.linqibin.commons.cqrs.CommandHandler;
import dev.linqibin.commons.error.field.FieldViolation;
import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.identity.domain.exception.InvalidCredentialsException;
import dev.linqibin.patra.identity.domain.exception.InvalidUserFieldsException;
import dev.linqibin.patra.identity.domain.exception.LoginTemporarilyLockedException;
import dev.linqibin.patra.identity.domain.exception.UserBannedException;
import dev.linqibin.patra.identity.domain.model.aggregate.User;
import dev.linqibin.patra.identity.domain.model.aggregate.UserPasswordCredential;
import dev.linqibin.patra.identity.domain.model.vo.EmailAddress;
import dev.linqibin.patra.identity.domain.model.vo.PlainPassword;
import dev.linqibin.patra.identity.domain.port.hashing.PasswordHashingPort;
import dev.linqibin.patra.identity.domain.port.repository.UserPasswordCredentialRepository;
import dev.linqibin.patra.identity.domain.port.repository.UserRepository;
import dev.linqibin.patra.identity.domain.port.throttle.LoginAttempt;
import dev.linqibin.patra.identity.domain.port.throttle.LoginThrottlePort;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 校验前台用户的登录凭据。
///
/// 1. 校验字段：邮箱用注册时的规则，密码只查非空和 Unicode 合法。
/// 2. 开始一次尝试：锁定期内或在途已满直接 429，不查库、不做哈希。
/// 3. 查用户和凭据。查不到时拿假哈希做一次校验，耗时和「密码错」一样。
/// 4. 密码错按失败结算（可能触发 429）；密码对按成功结算，封禁的账号返回 403。
/// 5. 中途出错按取消结算，不计失败，原来的错误照常抛出。
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticateUserHandler
    implements CommandHandler<AuthenticateUserCommand, AuthenticateUserResult> {

  private final UserRepository users;
  private final UserPasswordCredentialRepository credentials;
  private final PasswordHashingPort passwordHashing;
  private final LoginThrottlePort loginThrottle;

  /// 校验凭据。
  ///
  /// @param command 命令
  /// @return 用户 ID 和邮箱
  @Override
  public AuthenticateUserResult handle(AuthenticateUserCommand command) {
    List<FieldViolation> violations = new ArrayList<>();
    EmailAddress.validate(command.email()).ifPresent(violations::add);
    PlainPassword.validate(command.password()).ifPresent(violations::add);
    if (!violations.isEmpty()) {
      throw new InvalidUserFieldsException(violations);
    }
    EmailAddress email = EmailAddress.of(command.email());
    PlainPassword password = PlainPassword.of(command.password());

    LoginAttempt attempt = loginThrottle.begin(AccountType.USER, email);
    Optional<User> user;
    boolean matches;
    try {
      user = users.findByEmail(email);
      matches = verify(user, password);
    } catch (RuntimeException e) {
      cancelQuietly(attempt);
      throw e;
    }
    if (!matches) {
      Optional<Duration> lock = loginThrottle.recordFailure(attempt);
      if (lock.isPresent()) {
        throw new LoginTemporarilyLockedException(lock.get());
      }
      throw new InvalidCredentialsException();
    }
    loginThrottle.recordSuccess(attempt);
    User verified = user.orElseThrow();
    if (verified.isBanned()) {
      throw new UserBannedException();
    }
    return AuthenticateUserResult.of(verified.getId(), verified.getEmail().value());
  }

  /// 校验密码。用户或凭据不存在时拿假哈希校验一次，然后返回 `false`。
  ///
  /// @param user 按邮箱查到的用户
  /// @param password 明文密码
  /// @return 匹配时为 `true`
  private boolean verify(Optional<User> user, PlainPassword password) {
    Optional<UserPasswordCredential> credential =
        user.flatMap(found -> credentials.findByUserId(found.getId()));
    if (credential.isEmpty()) {
      passwordHashing.verifyAgainstDummy(password);
      return false;
    }
    return passwordHashing.matches(password, credential.get().getPasswordHash());
  }

  /// 按取消结算。取消失败只记日志：在途登记会自动过期，不能盖掉原来的错误。
  ///
  /// @param attempt 尝试
  private void cancelQuietly(LoginAttempt attempt) {
    try {
      loginThrottle.cancel(attempt);
    } catch (RuntimeException e) {
      log.warn("取消登录尝试失败，在途登记会自动过期: {}", e.getMessage());
    }
  }
}
