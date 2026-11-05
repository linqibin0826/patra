package dev.patra.identity.app.account.usecase.login;

import dev.patra.identity.app.account.service.HumanVerificationGuard;
import dev.patra.identity.app.account.service.NewDeviceLoginNotifier;
import dev.patra.identity.app.account.service.ThrottledPasswordCheck;
import dev.patra.identity.domain.account.exception.InvalidCredentialsException;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.aggregate.UserPasswordCredential;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.PlainPassword;
import dev.patra.identity.domain.account.model.vo.RequestOrigin;
import dev.patra.identity.domain.account.policy.HumanVerificationPolicy;
import dev.patra.identity.domain.account.port.hashing.PasswordHashingPort;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.account.port.repository.UserPasswordCredentialRepository;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.account.port.throttle.LoginIpFailurePort;
import dev.patra.identity.domain.account.service.ClientOriginResolver;
import dev.patra.identity.domain.session.model.vo.ClientOrigin;
import dev.patra.identity.domain.session.model.vo.IssuedUserSession;
import dev.patra.identity.domain.session.model.vo.LoginClient;
import dev.patra.identity.domain.session.service.SessionIssuer;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 前台用户登录：校验凭据，通过后建会话。
///
/// 1. 解析字段（{@link LoginInput}），错误一次报全，不碰失败限制。
/// 2. 人机验证（{@link HumanVerificationGuard}）：在事务外、开始尝试之前。已锁定直接 429；同一邮箱或同一 IP 的失败次数
///    到门槛时要先过验证，没过的请求不占失败限制的名额，也不做哈希。
/// 3. 认证（{@link ThrottledPasswordCheck}）：锁定期内或在途已满直接 429，不查库、不做哈希。查用户和凭据，
///    查不到时拿假哈希做一次校验，耗时和「密码错」一样。密码错按失败结算（可能触发 429）；密码对但账号已注销
///    也按失败结算，这些失败同时计入这个 IP；密码对按成功结算，IP 计数不动；中途出错按取消结算，不计失败，原来的错误照常抛出。
/// 4. 检查资格：封禁的账号返回 403，冷静期内的账号照常登录（响应里带状态）。放在密码校验之后，
///    不知道密码的人试探不出账号状态。
/// 5. 发会话：在一个事务里先对用户行加锁重读、复核资格，再建会话：存登录记录、写 Redis、
///    收尾被挤掉的记录。封禁对同一行的更新和这把锁互斥：封禁先提交，这里返回 403；登录先提交，
///    封禁随后的「删全部会话」会把这条新会话一起删掉。哈希在事务外，事务不占着连接等排队。
///    Redis 写失败回滚记录，返回 503。
/// 6. 提醒新设备（PAP-99）：校验通过后、事务之外解析 IP 与 UA，登录记录带上；事务里建会话之前判定
///    这个账号是否见过这台设备，提交后对没见过的设备取 24 小时限流、放行才另开一个短事务登记新设备提醒
///    （PAP-104）。限流在 Redis，不能放进登录事务（`transaction.md` 第 2 节），所以两次提交之间进程崩了会丢这一封。
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginUserHandler implements CommandHandler<LoginUserCommand, AuthenticatedUser> {

  private final UserRepository users;
  private final UserPasswordCredentialRepository credentials;
  private final UserReadPort userReadPort;
  private final PasswordHashingPort passwordHashing;
  private final ThrottledPasswordCheck passwordCheck;
  private final HumanVerificationGuard verificationGuard;
  private final LoginIpFailurePort ipFailures;
  private final Supplier<HumanVerificationPolicy> verificationPolicy;
  private final SessionIssuer sessionIssuer;
  private final ClientOriginResolver clientOriginResolver;
  private final NewDeviceLoginNotifier newDeviceNotifier;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 登录。
  ///
  /// @param command 命令
  /// @return 会话令牌和账号信息
  @Override
  public AuthenticatedUser handle(LoginUserCommand command) {
    LoginInput input = LoginInput.parse(command);
    verificationGuard.requireForLogin(
        input.email(), command.clientIp(), command.verificationToken());
    User user = authenticate(input.email(), input.password(), command.clientIp());
    user.requireLoginEligible();
    // 解析 UA 与属地是纯计算：凭据校验通过之后、事务之外
    ClientOrigin origin =
        clientOriginResolver.resolve(RequestOrigin.of(command.clientIp(), command.userAgent()));
    Instant now = clock.instant();
    Login login = issueSession(user.getId(), input.client(), origin, now);
    log.info(
        "前台用户已登录: userId={}, status={}, replacedSessions={}",
        login.account().userId(),
        login.account().status(),
        login.session().replacedSessionIds().size());
    if (login.newDevice()) {
      newDeviceNotifier.notifyNewDevice(login.account(), input.client(), origin, now);
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

  /// 认证：在失败限制下校验邮箱和密码。
  ///
  /// @param email 邮箱
  /// @param password 明文密码
  /// @param clientIp 客户端 IP，可以为 `null`
  /// @return 通过校验的用户，状态是事务前预读的，发会话时还要在锁内复核
  private User authenticate(EmailAddress email, PlainPassword password, String clientIp) {
    return passwordCheck.run(
        email,
        password,
        () -> findVerifiedUser(email, password),
        InvalidCredentialsException::new,
        () -> recordIpFailureQuietly(clientIp));
  }

  /// 记一次 IP 失败。出错只记日志：这次登录的结果已经定了，不能被盖掉。
  ///
  /// @param clientIp 客户端 IP，可以为 `null`
  private void recordIpFailureQuietly(String clientIp) {
    try {
      ipFailures.record(clientIp, verificationPolicy.get().ipFailureWindow());
    } catch (RuntimeException e) {
      log.warn("记录 IP 登录失败出错: {}", e.getMessage());
    }
  }

  /// 按邮箱查用户并校验密码。先校验再看状态：已注销的用户邮箱已释放、凭据已删，正常按邮箱查不到；
  /// 真查到了也和密码错误一样处理，所以这里返回空，由调用方按失败结算。
  ///
  /// @param email 邮箱
  /// @param password 明文密码
  /// @return 通过校验且未注销的用户；否则为空
  private Optional<User> findVerifiedUser(EmailAddress email, PlainPassword password) {
    Optional<User> user = users.findByEmail(email);
    return verify(user, password) ? user.filter(found -> !found.isDeleted()) : Optional.empty();
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

  /// 在一个事务里锁用户行、复核资格、判定新设备、建会话、读账号信息。
  ///
  /// @param userId 用户 ID
  /// @param client 客户端信息
  /// @param origin 请求来源
  /// @param now 当前时间
  /// @return 会话、账号信息和新设备判定
  private Login issueSession(long userId, LoginClient client, ClientOrigin origin, Instant now) {
    return Objects.requireNonNull(
        transactions.execute(
            status -> {
              User locked =
                  users.findByIdForUpdate(userId).orElseThrow(InvalidCredentialsException::new);
              locked.requireLoginEligible();
              // 建会话之前判：之后再查会查到刚建的这条记录
              boolean seen = newDeviceNotifier.seenBefore(userId, client, now);
              IssuedUserSession session = sessionIssuer.issue(userId, client, origin);
              UserAccountReadModel account =
                  userReadPort.findAccount(userId).orElseThrow(InvalidCredentialsException::new);
              return new Login(session, account, !seen);
            }));
  }
}
