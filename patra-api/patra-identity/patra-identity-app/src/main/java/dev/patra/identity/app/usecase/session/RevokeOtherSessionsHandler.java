package dev.patra.identity.app.usecase.session;

import dev.patra.identity.domain.model.aggregate.UserLoginRecord;
import dev.patra.identity.domain.model.enums.LoginEndReason;
import dev.patra.identity.domain.port.repository.UserLoginRecordRepository;
import dev.patra.identity.domain.port.throttle.NewDeviceMailThrottlePort;
import dev.patra.identity.domain.service.ReauthenticationGuard;
import dev.patra.identity.domain.service.SessionTerminator;
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
