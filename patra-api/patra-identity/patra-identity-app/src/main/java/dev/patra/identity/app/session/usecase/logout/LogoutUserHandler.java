package dev.patra.identity.app.session.usecase.logout;

import dev.patra.identity.domain.session.model.enums.LoginEndReason;
import dev.patra.identity.domain.session.port.repository.UserLoginRecordRepository;
import dev.patra.identity.domain.session.port.store.SessionStorePort;
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

/// 登出：有当前用户就删掉它的会话、把登录记录标成 `LOGOUT`；没有就什么都不做。
///
/// 先删 Redis 再改记录：删会话才是登出的实质，记录没改成也不影响用户已经登出，
/// 所以结束记录时数据库出错只记 WARN、照样 204，记录留空。
/// 会话已经不在（过期、封禁、被挤掉）时不动记录：结束原因以先发生的为准。
/// 登出在网关上是公开路由，所以这里用 `current()` 不用 `require()`。
@Slf4j
@Component
@RequiredArgsConstructor
public class LogoutUserHandler implements CommandHandler<LogoutUserCommand, Void> {

  private final CurrentUserPort currentUserPort;
  private final SessionStorePort sessions;
  private final UserLoginRecordRepository records;
  private final Clock clock;
  private final TransactionOperations transactions;

  /// 登出。
  ///
  /// @param command 命令
  /// @return `null`
  @Override
  public Void handle(LogoutUserCommand command) {
    Optional<CurrentUser> current = currentUserPort.current();
    if (current.isEmpty()) {
      return null;
    }
    CurrentUser user = current.get();
    boolean revoked = sessions.revoke(user.accountType(), user.userId(), user.sessionId());
    if (!revoked) {
      log.info("登出时会话已不存在: userId={}, sessionId={}", user.userId(), user.sessionId());
      return null;
    }
    Instant now = clock.instant();
    try {
      transactions.executeWithoutResult(
          status ->
              records
                  .findById(user.sessionId())
                  .ifPresentOrElse(
                      login -> {
                        login.end(LoginEndReason.LOGOUT, now);
                        records.save(login);
                      },
                      () ->
                          log.info(
                              "登出时找不到登录记录: userId={}, sessionId={}",
                              user.userId(),
                              user.sessionId())));
    } catch (RuntimeException e) {
      log.warn("登出后结束登录记录失败，记录留空: userId={}, sessionId={}", user.userId(), user.sessionId(), e);
    }
    log.info("前台用户已登出: userId={}, sessionId={}", user.userId(), user.sessionId());
    return null;
  }
}
