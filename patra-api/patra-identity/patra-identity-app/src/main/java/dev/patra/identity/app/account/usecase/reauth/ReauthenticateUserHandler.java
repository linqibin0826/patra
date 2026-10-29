package dev.patra.identity.app.account.usecase.reauth;

import dev.patra.identity.app.account.service.CurrentPasswordVerifier;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.PlainPassword;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.session.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.session.model.vo.SessionRotation;
import dev.patra.identity.domain.session.policy.ReauthenticationPolicy;
import dev.patra.identity.domain.session.port.store.SessionStorePort;
import dev.patra.tool.core.security.AuthenticationRequiredException;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 再认证：校验当前用户的密码，通过后换新令牌（保留会话 ID）并刷新 `auth_at`。
///
/// 密码校验和登录共用失败限制：错计失败、锁定期 429。`ACTIVE` 与 `PENDING_DELETION` 可再认证
/// （冷静期内能登录就能再认证）；`BANNED` / `DELETED` 401。不开事务：没有数据库写，
/// 封禁的 `revokeAll` 和这里的 `rotate` 都是 Redis 原子脚本，谁后执行谁生效。
///
/// 并发再认证：`auth_at` 距今不足宽限期（`ReauthenticationPolicy.rotationGrace`）时脚本原子地判定不换，
/// 结果里没有新令牌，门户保持现有 Cookie——它最终是先换出的那个，两个标签页都不会被登出。
@Slf4j
@Component
@RequiredArgsConstructor
public class ReauthenticateUserHandler
    implements CommandHandler<ReauthenticateUserCommand, Reauthenticated> {

  private final CurrentUserPort currentUserPort;
  private final UserRepository users;
  private final UserReadPort userReadPort;
  private final CurrentPasswordVerifier passwordVerifier;
  private final SessionStorePort sessions;
  private final ReauthenticationPolicy policy;

  /// 再认证。
  ///
  /// @param command 命令
  /// @return 账号信息和新令牌（宽限期内没换时为 `null`）
  @Override
  public Reauthenticated handle(ReauthenticateUserCommand command) {
    CurrentUser current = currentUserPort.require();
    PlainPassword.validate(command.password())
        .ifPresent(
            violation -> {
              throw new InvalidUserFieldsException(List.of(violation));
            });
    PlainPassword password = PlainPassword.of(command.password());
    long userId = current.userId();
    User user = users.findById(userId).orElseThrow(AuthenticationRequiredException::new);
    if (user.isBanned() || user.isDeleted()) {
      throw new AuthenticationRequiredException();
    }
    EmailAddress email = user.getEmail().orElseThrow(AuthenticationRequiredException::new);
    passwordVerifier.verify(email, userId, password);
    UserAccountReadModel account =
        userReadPort.findAccount(userId).orElseThrow(AuthenticationRequiredException::new);
    SessionRotation rotation =
        sessions
            .rotate(current.accountType(), userId, current.sessionId(), policy.rotationGrace())
            .orElseThrow(AuthenticationRequiredException::new);
    log.info(
        "前台用户已再认证: userId={}, sessionId={}, rotated={}",
        userId,
        current.sessionId(),
        rotation.rotated());
    return Reauthenticated.of(rotation.newToken().orElse(null), account);
  }
}
