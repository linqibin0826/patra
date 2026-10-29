package dev.patra.identity.app.account.usecase.login;

import dev.patra.identity.domain.account.exception.InvalidCredentialsException;
import dev.patra.identity.domain.account.exception.LoginTemporarilyLockedException;
import dev.patra.identity.domain.account.exception.UserBannedException;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.aggregate.UserPasswordCredential;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.PlainPassword;
import dev.patra.identity.domain.account.model.vo.RequestOrigin;
import dev.patra.identity.domain.account.model.vo.mail.NewDeviceLoginMail;
import dev.patra.identity.domain.account.policy.PasswordPolicy;
import dev.patra.identity.domain.account.port.hashing.PasswordHashingPort;
import dev.patra.identity.domain.account.port.mail.EmailDeliveryPort;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.account.port.repository.UserPasswordCredentialRepository;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.account.port.throttle.LoginAttempt;
import dev.patra.identity.domain.account.port.throttle.LoginThrottlePort;
import dev.patra.identity.domain.account.service.ClientOriginResolver;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.session.model.vo.ClientOrigin;
import dev.patra.identity.domain.session.model.vo.IssuedUserSession;
import dev.patra.identity.domain.session.model.vo.LoginClient;
import dev.patra.identity.domain.session.policy.NewDeviceLoginPolicy;
import dev.patra.identity.domain.session.port.repository.UserLoginRecordRepository;
import dev.patra.identity.domain.session.port.throttle.NewDeviceMailThrottlePort;
import dev.patra.identity.domain.session.service.SessionIssuer;
import dev.patra.tool.core.error.field.FieldViolation;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
/// 4. 密码错按失败结算（可能触发 429）；密码对但账号已注销也按失败结算；密码对按成功结算，
///    封禁的账号返回 403，冷静期内的账号照常登录（响应里带状态）。
/// 5. 中途出错按取消结算，不计失败，原来的错误照常抛出。
/// 6. 校验通过后在一个事务里先对用户行加锁重读、复核封禁，再建会话：存登录记录、写 Redis、
///    收尾被挤掉的记录。封禁对同一行的更新和这把锁互斥：封禁先提交，这里返回 403；登录先提交，
///    封禁随后的「删全部会话」会把这条新会话一起删掉。哈希在事务外，事务不占着连接等排队。
///    Redis 写失败回滚记录，返回 503。
/// 7. 设备与属地（PAP-99）：校验通过后、事务之外解析 IP 与 UA，登录记录带上；事务里建会话之前判定
///    这个账号是否见过这台设备，提交后对没见过的设备取 24 小时限流、放行才发新设备提醒。
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginUserHandler implements CommandHandler<LoginUserCommand, AuthenticatedUser> {

  private final UserRepository users;
  private final UserPasswordCredentialRepository credentials;
  private final UserReadPort userReadPort;
  private final PasswordHashingPort passwordHashing;
  private final LoginThrottlePort loginThrottle;
  private final SessionIssuer sessionIssuer;
  private final ClientOriginResolver clientOriginResolver;
  private final UserLoginRecordRepository records;
  private final NewDeviceLoginPolicy newDevicePolicy;
  private final NewDeviceMailThrottlePort newDeviceThrottle;
  private final EmailDeliveryPort emailDelivery;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 登录。
  ///
  /// @param command 命令
  /// @return 会话令牌和账号信息
  @Override
  public AuthenticatedUser handle(LoginUserCommand command) {
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
    User verified = user.orElseThrow();
    if (verified.isDeleted()) {
      // 已注销的用户邮箱已释放、凭据已删，正常按邮箱查不到；真查到了也和密码错误一样处理
      throw failure(attempt);
    }
    loginThrottle.recordSuccess(attempt);
    if (verified.isBanned()) {
      throw new UserBannedException();
    }
    // 解析 UA 与属地是纯计算：凭据校验通过之后、事务之外
    ClientOrigin origin =
        clientOriginResolver.resolve(RequestOrigin.of(command.clientIp(), command.userAgent()));
    Instant now = clock.instant();
    Login login =
        Objects.requireNonNull(
            transactions.execute(
                status -> {
                  User locked =
                      users
                          .findByIdForUpdate(verified.getId())
                          .orElseThrow(InvalidCredentialsException::new);
                  if (locked.isDeleted()) {
                    throw new InvalidCredentialsException();
                  }
                  if (locked.isBanned()) {
                    throw new UserBannedException();
                  }
                  // 建会话之前判：之后再查会查到刚建的这条记录
                  boolean seen = seenBefore(locked.getId(), client, now);
                  IssuedUserSession session = sessionIssuer.issue(locked.getId(), client, origin);
                  UserAccountReadModel account =
                      userReadPort
                          .findAccount(locked.getId())
                          .orElseThrow(InvalidCredentialsException::new);
                  return new Login(session, account, !seen);
                }));
    log.info(
        "前台用户已登录: userId={}, status={}, replacedSessions={}",
        verified.getId(),
        verified.getStatus(),
        login.session().replacedSessionIds().size());
    if (login.newDevice()) {
      notifyNewDevice(login.account(), client, origin, now);
    }
    return AuthenticatedUser.of(login.session().token(), login.account());
  }

  /// 事务里一起产出的会话、账号信息和新设备判定。
  ///
  /// @param session 新会话
  /// @param account 账号信息
  /// @param newDevice 是不是这个账号没见过的设备
  private record Login(
      IssuedUserSession session, UserAccountReadModel account, boolean newDevice) {}

  /// 这个账号是否见过这台设备（PAP-99 设计第 12.2 节）。没带设备标识的一律算没见过。
  ///
  /// @param userId 用户 ID
  /// @param client 客户端信息
  /// @param now 当前时间
  /// @return 见过时为 `true`
  private boolean seenBefore(long userId, LoginClient client, Instant now) {
    return client
        .device()
        .map(device -> records.existsTrustedDevice(userId, device, newDevicePolicy.since(now)))
        .orElse(false);
  }

  /// 新设备登录：取 24 小时限流，放行才发提醒；发信被拒（队列满、已停机）时退还冷却。
  /// 限流的 Redis 不可用只记日志：提醒不能挡住登录。
  ///
  /// @param account 账号信息
  /// @param client 客户端信息
  /// @param origin 请求来源
  /// @param now 当前时间
  private void notifyNewDevice(
      UserAccountReadModel account, LoginClient client, ClientOrigin origin, Instant now) {
    boolean allowed;
    try {
      allowed = newDeviceThrottle.acquire(account.userId(), client.device().orElse(null), now);
    } catch (RuntimeException e) {
      log.warn("新设备提醒的限流不可用，本次不发信: userId={}, {}", account.userId(), e.getMessage());
      return;
    }
    if (!allowed) {
      return;
    }
    boolean accepted =
        emailDelivery.send(
            NewDeviceLoginMail.of(
                EmailAddress.of(account.email()),
                account.displayName(),
                origin.toNotice(now, NewDeviceLoginMail.NOT_ME_ADVICE)));
    if (accepted) {
      log.info("已发新设备登录提醒: userId={}", account.userId());
      return;
    }
    // 这封被丢掉了：退还冷却，否则这台设备 24 小时内再登录也不会提醒
    try {
      newDeviceThrottle.reset(account.userId(), client.device().orElse(null));
    } catch (RuntimeException e) {
      log.warn("新设备提醒没发出，退还冷却也失败: userId={}, {}", account.userId(), e.getMessage());
    }
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
