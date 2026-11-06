package dev.patra.identity.app.account.usecase.oauth;

import dev.patra.identity.app.account.service.NewDeviceLoginNotifier;
import dev.patra.identity.app.account.usecase.login.AuthenticatedUser;
import dev.patra.identity.domain.account.exception.InvalidCredentialsException;
import dev.patra.identity.domain.account.model.aggregate.ExternalIdentity;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.PendingRegistration;
import dev.patra.identity.domain.account.model.vo.ProviderAccount;
import dev.patra.identity.domain.account.model.vo.RequestOrigin;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.domain.account.policy.ProviderRegistrationPolicy;
import dev.patra.identity.domain.account.policy.UsernamePolicy;
import dev.patra.identity.domain.account.port.oauth.PendingRegistrationPort;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.account.port.repository.ExternalIdentityRepository;
import dev.patra.identity.domain.account.port.repository.UserRepository;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 第三方平台确认身份之后完成登录。
///
/// 按 `(provider, subject)` 找账号，永不按邮箱合并（v0.19 决策 D）。没找到就建待补填记录，账号要等邮箱验证通过才创建。
/// 找到了：事务里锁用户行（和封禁互斥，同 `LoginUserHandler`）、复核状态、更新外部身份、建不带密码证明的会话
/// （第三方登录不开放再认证窗口，决策 E）；提交后走新设备提醒。
@Slf4j
@Component
@RequiredArgsConstructor
public class CompleteProviderLoginHandler
    implements CommandHandler<CompleteProviderLoginCommand, ProviderLoginResult> {

  private final ExternalIdentityRepository identities;
  private final PendingRegistrationPort pendingRegistrations;
  private final UserRepository users;
  private final UserReadPort userReadPort;
  private final SessionIssuer sessionIssuer;
  private final ClientOriginResolver clientOriginResolver;
  private final NewDeviceLoginNotifier newDeviceNotifier;
  private final UsernamePolicy usernamePolicy;
  private final ProviderRegistrationPolicy registrationPolicy;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 完成登录。
  ///
  /// @param command 命令
  /// @return 已发会话，或要补填
  @Override
  public ProviderLoginResult handle(CompleteProviderLoginCommand command) {
    ProviderAccount account = command.account();
    Optional<ExternalIdentity> found =
        identities.findByProviderAndSubject(account.provider(), account.subject());
    if (found.isEmpty()) {
      return startRegistration(account);
    }
    long userId = found.get().getUserId();
    LoginClient client = clientOf(command.deviceId());
    // 解析 UA 与属地是纯计算：放在事务之外
    ClientOrigin origin =
        clientOriginResolver.resolve(RequestOrigin.of(command.clientIp(), command.userAgent()));
    Instant now = clock.instant();
    Login login =
        Objects.requireNonNull(
            transactions.execute(
                status -> {
                  User locked =
                      users.findByIdForUpdate(userId).orElseThrow(InvalidCredentialsException::new);
                  locked.requireLoginEligible();
                  // 锁内重读为准：并发登录靠这把锁串行，乐观锁不会撞
                  ExternalIdentity identity =
                      identities
                          .findByProviderAndSubject(account.provider(), account.subject())
                          .orElseThrow(InvalidCredentialsException::new);
                  identity.recordLogin(account.accountLabel(), now);
                  identities.save(identity);
                  // 建会话之前判：之后再查会查到刚建的这条记录
                  boolean seen = newDeviceNotifier.seenBefore(userId, client, now);
                  IssuedUserSession session =
                      sessionIssuer.issueWithoutPasswordProof(userId, client, origin);
                  UserAccountReadModel read =
                      userReadPort
                          .findAccount(userId)
                          .orElseThrow(InvalidCredentialsException::new);
                  return new Login(session, read, !seen);
                }));
    log.info(
        "前台用户已用第三方账号登录: userId={}, provider={}, status={}",
        userId,
        account.provider(),
        login.account().status());
    if (login.newDevice()) {
      newDeviceNotifier.notifyNewDevice(login.account(), client, origin, now);
    }
    return new ProviderLoginResult.Authenticated(
        AuthenticatedUser.of(login.session().token(), login.account()));
  }

  /// 事务里一起产出的会话、账号信息和新设备判定。
  ///
  /// @param session 新会话
  /// @param account 账号信息
  /// @param newDevice 是不是这个账号没见过的设备
  private record Login(
      IssuedUserSession session, UserAccountReadModel account, boolean newDevice) {}

  /// 建待补填记录。预填值过不了规则就留空：不能让补填页一打开就报错。
  ///
  /// @param account 平台确认过的账号
  /// @return 要补填
  private ProviderLoginResult startRegistration(ProviderAccount account) {
    PendingRegistration pending =
        PendingRegistration.builder()
            .provider(account.provider())
            .subject(account.subject())
            .accountLabel(account.accountLabel())
            .suggestedUsername(suggestedUsername(account.suggestedUsername()))
            .suggestedEmail(suggestedEmail(account.suggestedEmail()))
            .expiresAt(clock.instant().plus(registrationPolicy.pendingTtl()))
            .build();
    String ticket = pendingRegistrations.create(pending, registrationPolicy.pendingTtl());
    log.info("第三方账号还没有对应用户，等待补填: provider={}", account.provider());
    return new ProviderLoginResult.RegistrationRequired(ticket, pending);
  }

  /// 预填的用户名：过得了注册规则才给。
  ///
  /// @param raw 平台给的值，可以为 `null`
  /// @return 规范化之后的用户名；过不了规则时为 `null`
  private String suggestedUsername(String raw) {
    return usernamePolicy.validate(raw).isEmpty() ? Username.normalize(raw) : null;
  }

  /// 预填的邮箱：格式合法才给。
  ///
  /// @param raw 平台给的值，可以为 `null`
  /// @return 规范化之后的邮箱；不合法时为 `null`
  private static String suggestedEmail(String raw) {
    return EmailAddress.validate(raw).isEmpty() ? EmailAddress.of(raw).value() : null;
  }

  /// 回调请求里的设备标识：格式不对按没带处理。授权码已经用掉了，不能因此让用户重走一遍。
  ///
  /// @param rawDeviceId 设备标识，可以为 `null`
  /// @return 客户端信息
  private static LoginClient clientOf(String rawDeviceId) {
    return LoginClient.validate(null, rawDeviceId).isEmpty()
        ? LoginClient.of(null, rawDeviceId)
        : LoginClient.web();
  }
}
