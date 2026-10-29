package dev.patra.identity.app.usecase.session;

import dev.patra.identity.domain.exception.CurrentSessionRevocationException;
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
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 踢单个设备（PAP-99 设计第 11.2 节）。
///
/// 1. 踢的是本机 → 409 `CURRENT_SESSION`，先于再认证：门户弹密码框之前就该知道这条路不对。
/// 2. 再认证守卫：会话不在 401，超时 403 `REAUTH_REQUIRED`。
/// 3. 撤销会话并把登录记录结束为 `REVOKED`。会话已经不在（别处踢掉、过期、不是这个用户的）返回 0，
///    不回 404：范围限定在本人的索引里，没有枚举问题。
/// 4. 清掉这台设备的新设备提醒冷却：本人不认这台设备，它再登录要立即提醒，不等 24 小时。
///
/// 不锁用户行、不判账号状态：只动会话与登录记录，不改用户聚合。不发邮件。
///
/// 事务：撤销会话（先 Redis 后登录记录）包在一个短事务里，登录记录一起结束；库失败时会话已撤、记录未结束，
/// 方向安全。不锁用户行：不改用户状态，没有要挡的并发登录（transaction.md 第 2 节的会话例外）。清提醒冷却在提交之后。
@Slf4j
@Component
@RequiredArgsConstructor
public class RevokeSessionHandler implements CommandHandler<RevokeSessionCommand, SessionsRevoked> {

  private final CurrentUserPort currentUserPort;
  private final ReauthenticationGuard reauthenticationGuard;
  private final SessionTerminator sessionTerminator;
  private final UserLoginRecordRepository records;
  private final NewDeviceMailThrottlePort newDeviceThrottle;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 踢掉。
  ///
  /// @param command 命令
  /// @return 被踢掉的会话数，0 或 1
  @Override
  public SessionsRevoked handle(RevokeSessionCommand command) {
    CurrentUser current = currentUserPort.require();
    if (command.sessionId() == current.sessionId()) {
      throw new CurrentSessionRevocationException();
    }
    reauthenticationGuard.requireRecent(current);
    Instant now = clock.instant();
    boolean revoked =
        Objects.requireNonNull(
            transactions.execute(
                status ->
                    sessionTerminator.terminate(
                        current.userId(), command.sessionId(), LoginEndReason.REVOKED, now)));
    // 清提醒冷却与会话无关，放在提交之后（transaction.md 第 2 节）
    if (revoked) {
      records
          .findById(command.sessionId())
          .ifPresent(
              record ->
                  newDeviceThrottle.reset(current.userId(), record.getDeviceId().orElse(null)));
    }
    log.info(
        "已踢掉设备: userId={}, sessionId={}, revoked={}",
        current.userId(),
        command.sessionId(),
        revoked);
    return SessionsRevoked.of(revoked ? 1 : 0);
  }
}
