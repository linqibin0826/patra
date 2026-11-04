package dev.patra.identity.app.account.usecase.oauth;

import dev.patra.identity.domain.account.model.aggregate.ExternalIdentity;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.enums.IdentityProvider;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.vo.RequestOrigin;
import dev.patra.identity.domain.account.model.vo.mail.LoginMethodUnlinkedMail;
import dev.patra.identity.domain.account.policy.LoginMethodPolicy;
import dev.patra.identity.domain.account.port.mail.EmailDeliveryPort;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.account.port.repository.ExternalIdentityRepository;
import dev.patra.identity.domain.account.port.repository.UserPasswordCredentialRepository;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.account.service.ClientOriginResolver;
import dev.patra.identity.domain.session.model.vo.ClientOrigin;
import dev.patra.identity.domain.session.service.ReauthenticationGuard;
import dev.patra.tool.core.security.AuthenticationRequiredException;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 解绑一家第三方账号。本来就没绑是 204，不发邮件。
///
/// 顺序：状态（409 / 401）→ 这一家绑着吗 → 「最后一种登录方式」（409）→ 再认证（403）→ 事务里锁用户行、按当前的凭据和绑定数
/// 重判规则、删行、登记安全邮件。
///
/// 规则排在再认证之前：没有密码的人永远过不了再认证，放在后面这条规则就碰不到了，界面也会把他们错引去设置密码。
/// 事务里要重判：并发解绑两家、并发撤销改邮箱删凭据都要先拿这把行锁，否则两边都看到「还有另一种」，一起解绑后就进不来了。
@Slf4j
@Component
@RequiredArgsConstructor
public class UnlinkProviderAccountHandler
    implements CommandHandler<UnlinkProviderAccountCommand, Void> {

  private final CurrentUserPort currentUserPort;
  private final UserRepository users;
  private final UserReadPort userReadPort;
  private final UserPasswordCredentialRepository credentials;
  private final ExternalIdentityRepository identities;
  private final ReauthenticationGuard reauthenticationGuard;
  private final ClientOriginResolver clientOriginResolver;
  private final EmailDeliveryPort emailDelivery;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 解绑。
  ///
  /// @param command 命令
  /// @return `null`
  @Override
  public Void handle(UnlinkProviderAccountCommand command) {
    CurrentUser current = currentUserPort.require();
    long userId = current.userId();
    IdentityProvider provider = command.provider();
    users.findById(userId).orElseThrow(AuthenticationRequiredException::new).requireActive();
    if (identities.findByUserIdAndProvider(userId, provider).isEmpty()) {
      return null;
    }
    requireCanUnlink(userId);
    reauthenticationGuard.requireRecent(current);
    // 解析 UA 与属地是纯计算：放在事务之外
    ClientOrigin origin =
        clientOriginResolver.resolve(RequestOrigin.of(command.clientIp(), command.userAgent()));
    Instant now = clock.instant();
    transactions.executeWithoutResult(
        status -> {
          User locked =
              users.findByIdForUpdate(userId).orElseThrow(AuthenticationRequiredException::new);
          locked.requireActive();
          Optional<ExternalIdentity> identity =
              identities.findByUserIdAndProvider(userId, provider);
          if (identity.isEmpty()) {
            return;
          }
          requireCanUnlink(userId);
          identities.deleteByUserIdAndProvider(userId, provider);
          UserAccountReadModel read =
              userReadPort.findAccount(userId).orElseThrow(AuthenticationRequiredException::new);
          emailDelivery.send(
              LoginMethodUnlinkedMail.of(
                  locked.getEmail().orElseThrow(),
                  read.displayName(),
                  provider.displayName(),
                  identity.get().getAccountLabel(),
                  origin.toNotice(now)));
        });
    log.info("已解绑第三方账号: userId={}, provider={}", userId, provider);
    return null;
  }

  /// 解绑之后还要进得来。
  ///
  /// @param userId 用户 ID
  private void requireCanUnlink(long userId) {
    LoginMethodPolicy.requireCanUnlink(
        credentials.findByUserId(userId).isPresent(), identities.countByUserId(userId));
  }
}
