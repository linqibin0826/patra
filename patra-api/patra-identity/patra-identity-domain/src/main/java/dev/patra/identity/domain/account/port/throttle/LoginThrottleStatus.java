package dev.patra.identity.domain.account.port.throttle;

import java.time.Duration;
import java.util.Optional;

/// 一个邮箱当前的失败限制状态。
///
/// @param failures 计数窗口内的失败次数
/// @param lockRemaining 处于锁定期时是锁的剩余时间，否则为空
public record LoginThrottleStatus(int failures, Optional<Duration> lockRemaining) {

  /// 创建状态。
  ///
  /// @param failures 失败次数
  /// @param lockRemaining 锁的剩余时间
  /// @return 状态
  public static LoginThrottleStatus of(int failures, Optional<Duration> lockRemaining) {
    return new LoginThrottleStatus(failures, lockRemaining);
  }
}
