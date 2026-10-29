package dev.patra.identity.app.account.usecase.deletion;

import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.RequestOrigin;
import dev.patra.identity.domain.account.model.vo.mail.AccountDeletionCancelledMail;
import dev.patra.identity.domain.account.port.mail.EmailDeliveryPort;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.account.service.ClientOriginResolver;
import dev.patra.identity.domain.session.model.vo.ClientOrigin;
import dev.patra.tool.core.security.AuthenticationRequiredException;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 取消注销（PAP-100 设计第 7.2 节）：加锁读用户，冷静期内回到正常；本来就正常时什么都不改（两个标签页先后点）。
/// 只有真的从冷静期回到正常才在提交后发「已取消注销」。
///
/// 不要求再认证：取消是往安全方向走，冷静期内的会话本来就是刚输过密码登录得来的。会话不动。
/// 封禁、已注销 401：这两种状态没有有效会话，与 `requireActive()` 一致。
@Slf4j
@Component
@RequiredArgsConstructor
public class CancelAccountDeletionHandler
    implements CommandHandler<CancelAccountDeletionCommand, UserAccountReadModel> {

  private final CurrentUserPort currentUserPort;
  private final UserRepository users;
  private final UserReadPort userReadPort;
  private final ClientOriginResolver clientOriginResolver;
  private final EmailDeliveryPort emailDelivery;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 取消注销。
  ///
  /// @param command 命令
  /// @return 取消之后的账号信息（与 `/auth/me` 同构）
  @Override
  public UserAccountReadModel handle(CancelAccountDeletionCommand command) {
    long userId = currentUserPort.require().userId();
    Cancelled cancelled =
        Objects.requireNonNull(
            transactions.execute(
                status -> {
                  User user =
                      users
                          .findByIdForUpdate(userId)
                          .orElseThrow(AuthenticationRequiredException::new);
                  if (user.isBanned() || user.isDeleted()) {
                    throw new AuthenticationRequiredException();
                  }
                  boolean pending = user.isPendingDeletion();
                  if (pending) {
                    user.cancelDeletion();
                    users.save(user);
                  }
                  UserAccountReadModel account =
                      userReadPort
                          .findAccount(userId)
                          .orElseThrow(AuthenticationRequiredException::new);
                  EmailAddress email =
                      user.getEmail().orElseThrow(AuthenticationRequiredException::new);
                  return new Cancelled(account, email, pending);
                }));
    if (cancelled.changed()) {
      Instant now = clock.instant();
      ClientOrigin origin =
          clientOriginResolver.resolve(RequestOrigin.of(command.clientIp(), command.userAgent()));
      boolean accepted =
          emailDelivery.send(
              AccountDeletionCancelledMail.of(
                  cancelled.email(),
                  cancelled.account().displayName(),
                  origin.toNotice(now, AccountDeletionCancelledMail.NOT_ME_ADVICE)));
      if (!accepted) {
        log.warn("「已取消注销」邮件被发信执行器拒收: userId={}", userId);
      }
      log.info("前台用户已取消注销: userId={}", userId);
    }
    return cancelled.account();
  }

  /// 事务里一起产出的账号信息、收件人，以及这次是不是真的从冷静期回到了正常。
  ///
  /// @param account 账号信息
  /// @param email 收件人
  /// @param changed 真的取消了时为 `true`
  private record Cancelled(UserAccountReadModel account, EmailAddress email, boolean changed) {}
}
