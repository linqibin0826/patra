package dev.patra.identity.app.usecase.emailchange;

import dev.patra.identity.domain.model.aggregate.User;
import dev.patra.identity.domain.model.enums.EmailTokenPurpose;
import dev.patra.identity.domain.port.repository.EmailTokenRepository;
import dev.patra.identity.domain.port.repository.UserRepository;
import dev.patra.tool.core.security.AuthenticationRequiredException;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/// 取消待确认的改邮箱：作废该用户的 `EMAIL_CHANGE_CONFIRM` 令牌。没有待确认也成功（幂等）。
/// 不要求再认证：取消是把门关上，不是打开。
@Slf4j
@Component
@RequiredArgsConstructor
public class CancelEmailChangeHandler implements CommandHandler<CancelEmailChangeCommand, Void> {

  private final CurrentUserPort currentUserPort;
  private final UserRepository users;
  private final EmailTokenRepository tokens;
  private final Clock clock;

  /// 取消。
  ///
  /// @param command 命令
  /// @return `null`
  @Override
  @Transactional
  public Void handle(CancelEmailChangeCommand command) {
    CurrentUser current = currentUserPort.require();
    User user =
        users.findByIdForUpdate(current.userId()).orElseThrow(AuthenticationRequiredException::new);
    user.requireActive();
    int revoked =
        tokens.revokeActive(
            current.userId(), EmailTokenPurpose.EMAIL_CHANGE_CONFIRM, clock.instant());
    log.info("待确认的改邮箱已取消: userId={}, revokedTokens={}", current.userId(), revoked);
    return null;
  }
}
