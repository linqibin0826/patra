package dev.patra.notification.app.kernel.service;

import dev.patra.notification.domain.kernel.port.gateway.PresenceGateway;
import dev.patra.notification.domain.kernel.port.repository.PresenceRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 在站信号的实现：一次主键 upsert，不限频（规模小；精确的时刻对「10 分钟窗口」判断有用）。
@Component
@RequiredArgsConstructor
public class PresenceGatewayImpl implements PresenceGateway {

  private final PresenceRepository presence;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 记下此刻在站上。
  ///
  /// @param userId 用户
  @Override
  public void markSeen(long userId) {
    Instant now = clock.instant();
    transactions.executeWithoutResult(status -> presence.touch(userId, now));
  }

  /// 批量读最后出现的时刻。
  ///
  /// @param userIds 用户
  /// @return 用户 ID → 时刻
  @Override
  public Map<Long, Instant> lastSeen(Collection<Long> userIds) {
    if (userIds.isEmpty()) {
      return Map.of();
    }
    return presence.find(userIds);
  }
}
