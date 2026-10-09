package dev.linqibin.patra.identity.app.usecase.login;

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
import dev.linqibin.patra.identity.domain.model.vo.LoginClient;
import dev.linqibin.patra.identity.domain.model.vo.PlainPassword;
import dev.linqibin.patra.identity.domain.policy.PasswordPolicy;
import dev.linqibin.patra.identity.domain.port.hashing.PasswordHashingPort;
import dev.linqibin.patra.identity.domain.port.repository.UserPasswordCredentialRepository;
import dev.linqibin.patra.identity.domain.port.repository.UserRepository;
import dev.linqibin.patra.identity.domain.port.session.IssuedUserSession;
import dev.linqibin.patra.identity.domain.port.throttle.LoginAttempt;
import dev.linqibin.patra.identity.domain.port.throttle.LoginThrottlePort;
import dev.linqibin.patra.identity.domain.service.SessionIssuer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 前台用户登录：校验凭据，通过后建会话。
///
/// 1. 校验字段：邮箱用注册时的规则，密码只查非空和 Unicode 合法，客户端类型和设备标识一起报。
/// 2. 开始一次尝试：锁定期内或在途已满直接 429，不查库、不做哈希。密码超过登录长度上限时
///    也不查库、不做哈希，直接按失败结算。
/// 3. 查用户和凭据。查不到时拿假哈希做一次校验，耗时和「密码错」一样。
/// 4. 密码错按失败结算（可能触发 429）；密码对按成功结算，封禁的账号返回 403。
/// 5. 中途出错按取消结算，不计失败，原来的错误照常抛出。
/// 6. 校验通过后在一个事务里先对用户行加锁重读、复核封禁，再建会话：存登录记录、写 Redis、
///    收尾被挤掉的记录。封禁对同一行的更新和这把锁互斥：封禁先提交，这里返回 403；登录先提交，
///    封禁随后的「删全部会话」会把这条新会话一起删掉。哈希在事务外，事务不占着连接等排队。
///    Redis 写失败回滚记录，返回 503。
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginUserHandler implements CommandHandler<LoginUserCommand, LoginUserResult> {

  private final UserRepository users;
  private final UserPasswordCredentialRepository credentials;
  private final PasswordHashingPort passwordHashing;
  private final LoginThrottlePort loginThrottle;
  private final SessionIssuer sessionIssuer;
  private final TransactionOperations transactions;

  /// 登录。
  ///
  /// @param command 命令
  /// @return 会话令牌、用户 ID 和邮箱
  @Override
  public LoginUserResult handle(LoginUserCommand command) {
    List<FieldViolation> violations = new ArrayList<>();
    EmailAddress.validate(command.email()).ifPresent(violations::add);
    PlainPassword.validate(command.password()).ifPresent(violations::add);
    violations.addAll(LoginClient.validate(command.clientType(), command.deviceId()));
    if (!violations.isEmpty()) {
      throw new InvalidUserFieldsException(violations);
    }
    EmailAddress email = EmailAddress.of(command.email());
    PlainPassword password = PlainPassword.of(command.password());
    LoginClient client = LoginClient.of(command.clientType(), command.deviceId());

    LoginAttempt attempt = loginThrottle.begin(AccountType.USER, email);
    if (password.length() > PasswordPolicy.MAX_LOGIN_LENGTH) {
      // 不可能是合法密码：不查库、不做哈希，按密码错误结算
      throw failure(attempt);
    }
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
      throw failure(attempt);
    }
    loginThrottle.recordSuccess(attempt);
    User verified = user.orElseThrow();
    if (verified.isBanned()) {
      throw new UserBannedException();
    }
    IssuedUserSession session =
        Objects.requireNonNull(
            transactions.execute(
                status -> {
                  User locked =
                      users
                          .findByIdForUpdate(verified.getId())
                          .orElseThrow(InvalidCredentialsException::new);
                  if (locked.isBanned()) {
                    throw new UserBannedException();
                  }
                  return sessionIssuer.issue(locked.getId(), client);
                }));
    log.info(
        "前台用户已登录: userId={}, replacedSessions={}",
        verified.getId(),
        session.replacedSessionIds().size());
    return LoginUserResult.of(session.token(), verified.getId(), verified.getEmail().value());
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

  /// 按失败结算。这次失败让账号处于锁定期时是 429 的异常，否则是 401 的异常。
  ///
  /// @param attempt 尝试
  /// @return 要抛出的异常
  private RuntimeException failure(LoginAttempt attempt) {
    Optional<Duration> lock = loginThrottle.recordFailure(attempt);
    if (lock.isPresent()) {
      return new LoginTemporarilyLockedException(lock.get());
    }
    return new InvalidCredentialsException();
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
