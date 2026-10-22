package dev.patra.identity.app.usecase.session;

import dev.patra.identity.domain.model.enums.LoginEndReason;
import dev.patra.identity.domain.service.ReauthenticationGuard;
import dev.patra.identity.domain.service.SessionTerminator;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/// 踢掉除本机外的全部设备（PAP-99 设计第 11.3 节）：先过再认证守卫，再撤销其余会话并把登录记录结束为
/// `REVOKED`。没有其他设备时返回 0。不发邮件。
@Slf4j
@Component
@RequiredArgsConstructor
public class RevokeOtherSessionsHandler
    implements CommandHandler<RevokeOtherSessionsCommand, SessionsRevoked> {

  private final CurrentUserPort currentUserPort;
  private final ReauthenticationGuard reauthenticationGuard;
  private final SessionTerminator sessionTerminator;
  private final Clock clock;

  /// 踢掉其他全部。
  ///
  /// @param command 命令
  /// @return 被踢掉的会话数
  @Override
  @Transactional
  public SessionsRevoked handle(RevokeOtherSessionsCommand command) {
    CurrentUser current = currentUserPort.require();
    reauthenticationGuard.requireRecent(current);
    List<Long> revoked =
        sessionTerminator.terminateAllExcept(
            current.userId(), current.sessionId(), LoginEndReason.REVOKED, clock.instant());
    log.info("已踢掉其他设备: userId={}, revoked={}", current.userId(), revoked.size());
    return SessionsRevoked.of(revoked.size());
  }
}
