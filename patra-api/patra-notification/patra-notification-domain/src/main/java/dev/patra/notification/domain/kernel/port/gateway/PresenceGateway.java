package dev.patra.notification.domain.kernel.port.gateway;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;

/// 在站信号（决策 H）：用户最后一次出现在站上的时刻。
///
/// 两种信号都打到通知服务上：门户拉未读数（inbox）、推送连接的心跳（realtime，PAP-145）；提醒邮件（PAP-146）读它判断
/// 「提醒生成后的 10 分钟里在不在站上」。存表，部署重启后还在。实现在 `app.kernel.service`，时刻取注入的时钟。
public interface PresenceGateway {

  /// 记下这个用户此刻在站上。
  ///
  /// @param userId 用户
  void markSeen(long userId);

  /// 这些用户最后一次出现的时刻。
  ///
  /// @param userIds 用户
  /// @return 用户 ID → 时刻；从没出现过的不在结果里
  Map<Long, Instant> lastSeen(Collection<Long> userIds);
}
