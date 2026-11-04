package dev.patra.identity.app.account.service;

import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.mail.NewDeviceLoginMail;
import dev.patra.identity.domain.account.port.mail.EmailDeliveryPort;
import dev.patra.identity.domain.session.model.vo.ClientOrigin;
import dev.patra.identity.domain.session.model.vo.LoginClient;
import dev.patra.identity.domain.session.policy.NewDeviceLoginPolicy;
import dev.patra.identity.domain.session.port.repository.UserLoginRecordRepository;
import dev.patra.identity.domain.session.port.throttle.NewDeviceMailThrottlePort;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 新设备登录提醒：密码登录和第三方登录共用。
///
/// 登录事务里、建会话之前调 {@link #seenBefore} 判定，提交后对没见过的设备调 {@link #notifyNewDevice}。
/// 限流在 Redis，不能放进登录事务（`transaction.md` 第 2 节），所以两次提交之间进程崩了会丢这一封。
@Slf4j
@Component
@RequiredArgsConstructor
public class NewDeviceLoginNotifier {

  private final UserLoginRecordRepository records;
  private final NewDeviceLoginPolicy policy;
  private final NewDeviceMailThrottlePort throttle;
  private final EmailDeliveryPort emailDelivery;
  private final TransactionOperations transactions;

  /// 这个账号是否见过这台设备（PAP-99 设计第 12.2 节）。没带设备标识的一律算没见过。
  ///
  /// @param userId 用户 ID
  /// @param client 客户端信息
  /// @param now 当前时间
  /// @return 见过时为 `true`
  public boolean seenBefore(long userId, LoginClient client, Instant now) {
    return client
        .device()
        .map(device -> records.existsTrustedDevice(userId, device, policy.since(now)))
        .orElse(false);
  }

  /// 新设备登录：事务外取 24 小时限流，放行才在一个只写登记行的短事务里登记提醒；登记失败时退还冷却。
  /// 限流的 Redis 不可用、登记失败都只记日志：提醒不能挡住已经提交的登录。
  ///
  /// @param account 账号信息
  /// @param client 客户端信息
  /// @param origin 请求来源
  /// @param now 当前时间
  public void notifyNewDevice(
      UserAccountReadModel account, LoginClient client, ClientOrigin origin, Instant now) {
    boolean allowed;
    try {
      allowed = throttle.acquire(account.userId(), client.device().orElse(null), now);
    } catch (RuntimeException e) {
      log.warn("新设备提醒的限流不可用，本次不发信: userId={}, {}", account.userId(), e.getMessage());
      return;
    }
    if (!allowed) {
      return;
    }
    try {
      transactions.executeWithoutResult(
          status ->
              emailDelivery.send(
                  NewDeviceLoginMail.of(
                      EmailAddress.of(account.email()),
                      account.displayName(),
                      origin.toNotice(now))));
      log.info("已登记新设备登录提醒: userId={}", account.userId());
    } catch (RuntimeException e) {
      // 登录已经提交：提醒登记失败不让登录失败；退还冷却，否则这台设备 24 小时内再登录也不会提醒
      log.warn("新设备提醒登记失败: userId={}", account.userId(), e);
      try {
        throttle.reset(account.userId(), client.device().orElse(null));
      } catch (RuntimeException resetFailure) {
        log.warn("新设备提醒没登记上，退还冷却也失败: userId={}, {}", account.userId(), resetFailure.getMessage());
      }
    }
  }
}
