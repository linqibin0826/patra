package dev.patra.identity.app.account.usecase.oauth;

import dev.patra.identity.app.account.service.PendingRegistrationCodes;
import dev.patra.identity.app.account.usecase.login.AuthenticatedUser;
import dev.patra.identity.app.account.usecase.query.PendingRegistrationQueryService;
import dev.patra.identity.domain.account.exception.ProviderAccountTakenException;
import dev.patra.identity.domain.account.exception.ProviderAlreadyLinkedException;
import dev.patra.identity.domain.account.model.aggregate.ExternalIdentity;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.enums.RegistrationCodePurpose;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.vo.CodeCheck;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.PendingRegistration;
import dev.patra.identity.domain.account.model.vo.RegistrationCode;
import dev.patra.identity.domain.account.model.vo.RequestOrigin;
import dev.patra.identity.domain.account.model.vo.mail.LoginMethodLinkedMail;
import dev.patra.identity.domain.account.port.mail.EmailDeliveryPort;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.account.port.repository.ExternalIdentityRepository;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.account.service.ClientOriginResolver;
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
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 邮箱撞车时就地绑定：验证码对了才绑到已有账号并登录（v0.19 决策 D 的补充）。
///
/// 这里证明的是邮箱归属，不是按邮箱合并。不要求再认证：能收这个邮箱的人本来也能走找回密码。新会话也不算再认证，
/// 不写 `auth_at`。账号邮箱未验证时绝不绑：抢注者的密码还留着，绑上去就是预劫持。
///
/// 顺序：字段（422）→ 凭证（422）→ 校验验证码（Redis，原子，对了就用掉，并发只有一个赢）→ 事务外查账号取锁键 →
/// 事务里锁行、复核资格、绑定、记最近登录、登记安全邮件、发不带密码证明的会话 → 提交后删待补填记录。
///
/// 没资格和验证码不对同样回 `code` / `INVALID`：不多泄露账号状态。不走新设备提醒：绑定安全邮件已经带了设备和位置。
@Slf4j
@Component
@RequiredArgsConstructor
public class LinkPendingRegistrationHandler
    implements CommandHandler<LinkPendingRegistrationCommand, AuthenticatedUser> {

  private final PendingRegistrationQueryService pendingQueries;
  private final PendingRegistrationCodes codes;
  private final UserRepository users;
  private final ExternalIdentityRepository identities;
  private final UserReadPort userReadPort;
  private final SessionIssuer sessionIssuer;
  private final ClientOriginResolver clientOriginResolver;
  private final EmailDeliveryPort emailDelivery;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 绑定并发会话。
  ///
  /// @param command 命令
  /// @return 会话令牌和账号信息
  @Override
  public AuthenticatedUser handle(LinkPendingRegistrationCommand command) {
    List<FieldViolation> violations = new ArrayList<>();
    Optional<RegistrationCode> parsed = codes.parse(command.code(), violations);
    violations.addAll(LoginClient.validate(command.clientType(), command.deviceId()));
    if (!violations.isEmpty()) {
      throw new InvalidUserFieldsException(violations);
    }
    LoginClient client = LoginClient.of(command.clientType(), command.deviceId());
    PendingRegistration registration = pendingQueries.require(command.ticket());
    CodeCheck.Matched matched =
        codes.verify(command.ticket(), parsed.orElseThrow(), RegistrationCodePurpose.LINK);
    EmailAddress email = EmailAddress.of(matched.email());
    long userId =
        users
            .findByEmail(email)
            .filter(User::canReceiveProviderLink)
            .map(User::getId)
            .orElseThrow(LinkPendingRegistrationHandler::codeInvalid);
    // 解析 UA 与属地是纯计算：放在事务之外
    ClientOrigin origin =
        clientOriginResolver.resolve(RequestOrigin.of(command.clientIp(), command.userAgent()));
    Instant now = clock.instant();
    AuthenticatedUser authenticated =
        Objects.requireNonNull(
            transactions.execute(
                status -> {
                  User locked =
                      users
                          .findByIdForUpdate(userId)
                          .filter(User::canReceiveProviderLink)
                          .filter(user -> user.getEmail().filter(email::equals).isPresent())
                          .orElseThrow(LinkPendingRegistrationHandler::codeInvalid);
                  boolean newlyLinked = bind(userId, registration, now);
                  recordLogin(registration, now);
                  UserAccountReadModel account =
                      userReadPort
                          .findAccount(userId)
                          .orElseThrow(() -> new IllegalStateException("刚锁住的用户读不回来: " + userId));
                  if (newlyLinked) {
                    emailDelivery.send(
                        LoginMethodLinkedMail.builder()
                            .to(locked.getEmail().orElseThrow())
                            .displayName(account.displayName())
                            .providerName(registration.provider().displayName())
                            .accountLabel(registration.accountLabel())
                            .notice(origin.toNotice(now))
                            .build());
                  }
                  // 会话排最后：它写 Redis，不随事务回滚
                  IssuedUserSession session =
                      sessionIssuer.issueWithoutPasswordProof(userId, client, origin);
                  return AuthenticatedUser.of(session.token(), account);
                }));
    codes.deletePendingQuietly(command.ticket());
    log.info("第三方账号已就地绑定并登录: userId={}, provider={}", userId, registration.provider());
    return authenticated;
  }

  /// 绑定。已经绑到这个账号（两个标签页）按登录处理；唯一约束兜并发：用户行已锁，插入没成功只能是第三方账号被别人抢先。
  ///
  /// @param userId 账号
  /// @param registration 待补填记录
  /// @param now 当前时间
  /// @return 这次新绑上时为 `true`
  private boolean bind(long userId, PendingRegistration registration, Instant now) {
    Optional<ExternalIdentity> existing =
        identities.findByProviderAndSubject(registration.provider(), registration.subject());
    if (existing.isPresent()) {
      if (existing.get().getUserId() != userId) {
        throw new ProviderAccountTakenException();
      }
      return false;
    }
    if (identities.findByUserIdAndProvider(userId, registration.provider()).isPresent()) {
      throw new ProviderAlreadyLinkedException();
    }
    if (!identities.insertIfAbsent(
        ExternalIdentity.link(
            userId,
            registration.provider(),
            registration.subject(),
            registration.accountLabel(),
            now))) {
      throw new ProviderAccountTakenException();
    }
    return true;
  }

  /// 这次也算用它登录了一次：设置页的「最近登录」不留空。
  ///
  /// @param registration 待补填记录
  /// @param now 当前时间
  private void recordLogin(PendingRegistration registration, Instant now) {
    ExternalIdentity identity =
        identities
            .findByProviderAndSubject(registration.provider(), registration.subject())
            .orElseThrow(() -> new IllegalStateException("刚绑定的外部身份读不回来"));
    identity.recordLogin(registration.accountLabel(), now);
    identities.save(identity);
  }

  /// 没资格时的错误，和验证码不对一样。
  ///
  /// @return 字段错误
  private static InvalidUserFieldsException codeInvalid() {
    return new InvalidUserFieldsException(List.of(UserFieldViolations.codeInvalid()));
  }
}
