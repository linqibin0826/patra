package dev.patra.identity.app.usecase.session;

import dev.patra.identity.domain.exception.CurrentSessionRevocationException;
import dev.patra.identity.domain.model.enums.LoginEndReason;
import dev.patra.identity.domain.service.ReauthenticationGuard;
import dev.patra.identity.domain.service.SessionTerminator;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/// 踢单个设备（PAP-99 设计第 11.2 节）。
///
/// 1. 踢的是本机 → 409 `CURRENT_SESSION`，先于再认证：门户弹密码框之前就该知道这条路不对。
/// 2. 再认证守卫：会话不在 401，超时 403 `REAUTH_REQUIRED`。
/// 3. 撤销会话并把登录记录结束为 `REVOKED`。会话已经不在（别处踢掉、过期、不是这个用户的）返回 0，
///    不回 404：范围限定在本人的索引里，没有枚举问题。
///
/// 不锁用户行、不判账号状态：只动会话与登录记录，不改用户聚合。不发邮件。
@Slf4j
@Component
@RequiredArgsConstructor
public class RevokeSessionHandler implements CommandHandler<RevokeSessionCommand, SessionsRevoked> {

  private final CurrentUserPort currentUserPort;
  private final ReauthenticationGuard reauthenticationGuard;
  private final SessionTerminator sessionTerminator;
  private final Clock clock;

  /// 踢掉。
  ///
  /// @param command 命令
  /// @return 被踢掉的会话数，0 或 1
  @Override
  @Transactional
  public SessionsRevoked handle(RevokeSessionCommand command) {
    CurrentUser current = currentUserPort.require();
    if (command.sessionId() == current.sessionId()) {
      throw new CurrentSessionRevocationException();
    }
    reauthenticationGuard.requireRecent(current);
    boolean revoked =
        sessionTerminator.terminate(
            current.userId(), command.sessionId(), LoginEndReason.REVOKED, clock.instant());
    log.info(
        "已踢掉设备: userId={}, sessionId={}, revoked={}",
        current.userId(),
        command.sessionId(),
        revoked);
    return SessionsRevoked.of(revoked ? 1 : 0);
  }
}
