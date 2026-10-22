package dev.patra.identity.app.usecase.reauth;

import dev.patra.identity.app.usecase.auth.AuthenticatedUser;
import dev.patra.identity.app.usecase.auth.CurrentPasswordVerifier;
import dev.patra.identity.domain.exception.InvalidUserFieldsException;
import dev.patra.identity.domain.model.aggregate.User;
import dev.patra.identity.domain.model.read.UserAccountReadModel;
import dev.patra.identity.domain.model.vo.EmailAddress;
import dev.patra.identity.domain.model.vo.PlainPassword;
import dev.patra.identity.domain.port.read.UserReadPort;
import dev.patra.identity.domain.port.repository.UserRepository;
import dev.patra.identity.domain.port.session.SessionStorePort;
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
@Slf4j
@Component
@RequiredArgsConstructor
public class ReauthenticateUserHandler
    implements CommandHandler<ReauthenticateUserCommand, AuthenticatedUser> {

  private final CurrentUserPort currentUserPort;
  private final UserRepository users;
  private final UserReadPort userReadPort;
  private final CurrentPasswordVerifier passwordVerifier;
  private final SessionStorePort sessions;

  /// 再认证。
  ///
  /// @param command 命令
  /// @return 新令牌和账号信息
  @Override
  public AuthenticatedUser handle(ReauthenticateUserCommand command) {
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
    String token =
        sessions
            .rotate(current.accountType(), userId, current.sessionId())
            .orElseThrow(AuthenticationRequiredException::new);
    UserAccountReadModel account =
        userReadPort.findAccount(userId).orElseThrow(AuthenticationRequiredException::new);
    log.info("前台用户已再认证: userId={}, sessionId={}", userId, current.sessionId());
    return AuthenticatedUser.of(token, account);
  }
}
