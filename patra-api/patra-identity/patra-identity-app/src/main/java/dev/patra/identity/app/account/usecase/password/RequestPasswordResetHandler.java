package dev.patra.identity.app.account.usecase.password;

import dev.patra.identity.domain.account.exception.PasswordResetLimitedException;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.enums.EmailTokenPurpose;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.EmailTokenSecret;
import dev.patra.identity.domain.account.model.vo.mail.PasswordResetMail;
import dev.patra.identity.domain.account.policy.MailLinkPolicy;
import dev.patra.identity.domain.account.policy.PasswordResetPolicy;
import dev.patra.identity.domain.account.port.mail.EmailDeliveryPort;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.account.port.throttle.PasswordResetThrottlePort;
import dev.patra.identity.domain.account.service.EmailTokenIssuer;
import dev.patra.identity.domain.account.service.IssuedEmailToken;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.tool.cqrs.CommandHandler;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 找回密码的申请：一律 202、异步发信，未注册或已封禁的邮箱不发信。
///
/// 顺序：格式（422）→ 限流（在查用户之前、对任何邮箱都计数：只给已注册的计数，429 本身就成了
/// 「已注册」的信号）→ 查用户 → 正常 / 冷静期用户：事务里锁用户行并重判状态、签发 `PASSWORD_RESET`，
/// 事务后发信；未注册 / 已封禁（含锁行前刚被删或封禁的）：做同样的随机数与哈希计算后直接返回。
/// 不碰登录失败限制。
@Slf4j
@Component
public class RequestPasswordResetHandler
    implements CommandHandler<RequestPasswordResetCommand, PasswordResetRequested> {

  private final UserRepository users;
  private final UserReadPort userReadPort;
  private final PasswordResetThrottlePort passwordResetThrottle;
  private final PasswordResetPolicy policy;
  private final EmailTokenIssuer emailTokenIssuer;
  private final MailLinkPolicy links;
  private final EmailDeliveryPort emailDelivery;
  private final TransactionOperations transactions;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  /// 创建处理器。
  ///
  /// @param users 用户仓储
  /// @param userReadPort 读端口
  /// @param passwordResetThrottle 限流
  /// @param policy 限流规则（算下次可申请时间）
  /// @param emailTokenIssuer 令牌签发
  /// @param links 链接规则
  /// @param emailDelivery 发信端口
  /// @param transactions 事务
  /// @param clock 时钟
  public RequestPasswordResetHandler(
      UserRepository users,
      UserReadPort userReadPort,
      PasswordResetThrottlePort passwordResetThrottle,
      PasswordResetPolicy policy,
      EmailTokenIssuer emailTokenIssuer,
      MailLinkPolicy links,
      EmailDeliveryPort emailDelivery,
      TransactionOperations transactions,
      Clock clock) {
    this.users = users;
    this.userReadPort = userReadPort;
    this.passwordResetThrottle = passwordResetThrottle;
    this.policy = policy;
    this.emailTokenIssuer = emailTokenIssuer;
    this.links = links;
    this.emailDelivery = emailDelivery;
    this.transactions = transactions;
    this.clock = clock;
  }

  /// 申请。
  ///
  /// @param command 命令
  /// @return 邮箱和下次可申请时间，注册与否结构一样
  @Override
  public PasswordResetRequested handle(RequestPasswordResetCommand command) {
    EmailAddress.validate(command.email())
        .ifPresent(
            violation -> {
              throw new InvalidUserFieldsException(List.of(violation));
            });
    EmailAddress email = EmailAddress.of(command.email());
    Instant now = clock.instant();
    passwordResetThrottle
        .acquireRequest(email, command.clientIp(), now)
        .ifPresent(
            wait -> {
              throw new PasswordResetLimitedException(wait);
            });
    Optional<User> user = users.findByEmail(email).filter(found -> !found.isBanned());
    if (user.isEmpty()) {
      // 未注册或已封禁：做同样的随机数与哈希计算，耗时和签发一个量级；不写库、不发信
      EmailTokenSecret.generate(random).hash();
      log.info("找回密码申请指向不发信的邮箱（未注册或已封禁）");
      return PasswordResetRequested.of(email.value(), policy.nextRequestAt(now));
    }
    long userId = user.get().getId();
    Optional<Prepared> prepared =
        transactions.execute(
            status -> {
              // 锁下重判：查到用户和锁行之间被删或被封禁，走和未注册一样的静默分支
              Optional<User> locked =
                  users.findByIdForUpdate(userId).filter(found -> !found.isBanned());
              if (locked.isEmpty()) {
                return Optional.<Prepared>empty();
              }
              UserAccountReadModel account =
                  userReadPort.findAccount(userId).orElseThrow(IllegalStateException::new);
              IssuedEmailToken issued =
                  emailTokenIssuer.issue(
                      EmailTokenPurpose.PASSWORD_RESET, userId, email, command.clientIp());
              return Optional.of(
                  new Prepared(
                      issued,
                      PasswordResetMail.of(
                          email, account.displayName(), links.resetPasswordUrl(issued.secret()))));
            });
    if (prepared == null || prepared.isEmpty()) {
      log.info("找回密码申请的用户在锁行前已被删或被封禁，不发信");
      return PasswordResetRequested.of(email.value(), policy.nextRequestAt(now));
    }
    emailDelivery.send(prepared.get().mail());
    log.info("重设密码邮件已排队: userId={}, tokenId={}", userId, prepared.get().issued().token().getId());
    return PasswordResetRequested.of(email.value(), policy.nextRequestAt(now));
  }

  /// 事务里签发好的令牌和邮件。
  ///
  /// @param issued 已保存的令牌与原文
  /// @param mail 要发的邮件
  private record Prepared(IssuedEmailToken issued, PasswordResetMail mail) {}
}
