package dev.patra.identity.app.session.usecase.revoke;

import dev.patra.identity.domain.session.model.aggregate.UserLoginRecord;
import dev.patra.identity.domain.session.model.enums.LoginEndReason;
import dev.patra.identity.domain.session.port.repository.UserLoginRecordRepository;
import dev.patra.identity.domain.session.port.throttle.NewDeviceMailThrottlePort;
import dev.patra.identity.domain.session.service.ReauthenticationGuard;
import dev.patra.identity.domain.session.service.SessionTerminator;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 踢掉除本机外的全部设备（PAP-99 设计第 11.3 节）：先过再认证守卫，再撤销其余会话并把登录记录结束为
/// `REVOKED`，再清掉每台被踢设备的新设备提醒冷却（它们再登录要立即提醒）。没有其他设备时返回 0。不发邮件。
///
/// 事务：撤销会话（先 Redis 后登录记录）包在一个短事务里，登录记录一起结束；库失败时会话已撤、记录未结束，
/// 方向安全。不锁用户行：不改用户状态，没有要挡的并发登录（transaction.md 第 2 节的会话例外）。清提醒冷却在提交之后。
@Slf4j
@Component
@RequiredArgsConstructor
public class RevokeOtherSessionsHandler
    implements CommandHandler<RevokeOtherSessionsCommand, SessionsRevoked> {

  private final CurrentUserPort currentUserPort;
  private final ReauthenticationGuard reauthenticationGuard;
  private final SessionTerminator sessionTerminator;
  private final UserLoginRecordRepository records;
  private final NewDeviceMailThrottlePort newDeviceThrottle;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 踢掉其他全部。
  ///
  /// @param command 命令
  /// @return 被踢掉的会话数
  @Override
  public SessionsRevoked handle(RevokeOtherSessionsCommand command) {
    CurrentUser current = currentUserPort.require();
    reauthenticationGuard.requireRecent(current);
    Instant now = clock.instant();
    List<Long> revoked =
        Objects.requireNonNull(
            transactions.execute(
                status ->
                    sessionTerminator.terminateAllExcept(
                        current.userId(), current.sessionId(), LoginEndReason.REVOKED, now)));
    // 清提醒冷却与会话无关，放在提交之后（transaction.md 第 2 节）
    for (UserLoginRecord record : records.findAllByIds(revoked)) {
      newDeviceThrottle.reset(current.userId(), record.getDeviceId().orElse(null));
    }
    log.info("已踢掉其他设备: userId={}, revoked={}", current.userId(), revoked.size());
    return SessionsRevoked.of(revoked.size());
  }
}
