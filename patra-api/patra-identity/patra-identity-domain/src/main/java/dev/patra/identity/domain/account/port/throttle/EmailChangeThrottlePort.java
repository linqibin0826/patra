package dev.patra.identity.domain.account.port.throttle;

import dev.patra.identity.domain.session.exception.TemporarilyUnavailableException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/// 改邮箱的限流：每账号每天的申请上限。
///
/// 放行即已计数，之后的步骤失败也不退还。时间由调用方按注入的 `Clock` 传入。
/// Redis 不可用时抛 {@link TemporarilyUnavailableException}（503）。
public interface EmailChangeThrottlePort {

  /// 申请一次改邮箱：没超就计数并放行，否则告诉调用方还要等多久。
  ///
  /// @param userId 用户 ID
  /// @param now 当前时间
  /// @return 放行时为空；被限时是还要等的时长
  Optional<Duration> acquire(long userId, Instant now);
}
