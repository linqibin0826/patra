package dev.patra.identity.app.account.usecase.oauth;

import dev.patra.identity.domain.account.exception.LinkSessionMismatchException;
import dev.patra.identity.domain.account.exception.ProviderAccountTakenException;
import dev.patra.identity.domain.account.exception.ProviderAlreadyLinkedException;
import dev.patra.identity.domain.account.model.aggregate.ExternalIdentity;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.vo.ProviderAccount;
import dev.patra.identity.domain.account.model.vo.RequestOrigin;
import dev.patra.identity.domain.account.model.vo.mail.LoginMethodLinkedMail;
import dev.patra.identity.domain.account.port.mail.EmailDeliveryPort;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.account.port.repository.ExternalIdentityRepository;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.account.service.ClientOriginResolver;
import dev.patra.identity.domain.session.model.vo.ClientOrigin;
import dev.patra.tool.core.security.AuthenticationRequiredException;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 绑定回调：核对发起人后写外部身份。
///
/// 回调时的用户和会话必须和发起时一样，防绑定 CSRF：老王发起绑定后把回调链接发给小陈，小陈的会话带着它回来，老王的
/// GitHub 不能绑到小陈的账号上。门户的 `state` Cookie 是第一道，这里是第二道。
/// 不重新判再认证窗口：授权请求只活 10 分钟，发起时判过了。
///
/// 事务里锁用户行（和解绑、封禁互斥）后判两条唯一规则，新行走 `ON CONFLICT DO NOTHING`：别的用户并发抢同一个第三方账号时
/// 不会把事务撞废。已经绑给自己的同一个账号幂等返回，不发邮件。安全邮件与绑定同一事务登记。
@Slf4j
@Component
@RequiredArgsConstructor
public class LinkProviderAccountHandler
    implements CommandHandler<LinkProviderAccountCommand, ProviderLinked> {

  private final UserRepository users;
  private final UserReadPort userReadPort;
  private final ExternalIdentityRepository identities;
  private final ClientOriginResolver clientOriginResolver;
  private final EmailDeliveryPort emailDelivery;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 绑定。
  ///
  /// @param command 命令
  /// @return 平台和账号标识
  @Override
  public ProviderLinked handle(LinkProviderAccountCommand command) {
    CurrentUser current = command.current();
    if (current == null) {
      throw new AuthenticationRequiredException();
    }
    if (current.userId() != command.initiatorUserId()
        || current.sessionId() != command.initiatorSessionId()) {
      log.warn(
          "绑定回调的发起人对不上: initiatorUserId={}, currentUserId={}",
          command.initiatorUserId(),
          current.userId());
      throw new LinkSessionMismatchException();
    }
    ProviderAccount account = command.account();
    long userId = current.userId();
    // 解析 UA 与属地是纯计算：放在事务之外
    ClientOrigin origin =
        clientOriginResolver.resolve(RequestOrigin.of(command.clientIp(), command.userAgent()));
    Instant now = clock.instant();
    boolean newlyLinked =
        Objects.requireNonNull(
            transactions.execute(
                status -> {
                  User locked =
                      users
                          .findByIdForUpdate(userId)
                          .orElseThrow(AuthenticationRequiredException::new);
                  locked.requireActive();
                  Optional<ExternalIdentity> existing =
                      identities.findByProviderAndSubject(account.provider(), account.subject());
                  if (existing.isPresent()) {
                    if (existing.get().getUserId() != userId) {
                      throw new ProviderAccountTakenException();
                    }
                    return false;
                  }
                  if (identities.findByUserIdAndProvider(userId, account.provider()).isPresent()) {
                    throw new ProviderAlreadyLinkedException();
                  }
                  if (!identities.insertIfAbsent(
                      ExternalIdentity.link(
                          userId,
                          account.provider(),
                          account.subject(),
                          account.accountLabel(),
                          now))) {
                    throw new ProviderAccountTakenException();
                  }
                  UserAccountReadModel read =
                      userReadPort
                          .findAccount(userId)
                          .orElseThrow(AuthenticationRequiredException::new);
                  emailDelivery.send(
                      LoginMethodLinkedMail.of(
                          locked.getEmail().orElseThrow(),
                          read.displayName(),
                          account.provider().displayName(),
                          account.accountLabel(),
                          origin.toNotice(now)));
                  return true;
                }));
    if (newlyLinked) {
      log.info("已绑定第三方账号: userId={}, provider={}", userId, account.provider());
    }
    return ProviderLinked.of(account.provider(), account.accountLabel());
  }
}
