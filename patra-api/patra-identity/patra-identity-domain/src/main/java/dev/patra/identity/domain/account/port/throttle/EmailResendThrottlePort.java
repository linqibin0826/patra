package dev.patra.identity.domain.account.port.throttle;

import dev.patra.identity.domain.session.exception.TemporarilyUnavailableException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/// 重发验证邮件的限流：冷却 + 每账号每小时 / 每天 + 每 IP 每小时，四个维度一次判完、一次计数。
///
/// 时间由调用方按注入的 `Clock` 传入，实现不自己取时间，固定时钟下窗口能被拨过去。
/// Redis 不可用时两个方法都抛 {@link TemporarilyUnavailableException}（503）。
public interface EmailResendThrottlePort {

  /// 申请发一封：四个维度都没超就计数并放行，否则告诉调用方还要等多久（取各维度里最长的）。
  /// 放行即已计数，之后的步骤失败也不退还。
  ///
  /// @param userId 用户 ID
  /// @param clientIp 客户端 IP，可以为 `null`（未知时跳过每 IP 维度）
  /// @param now 当前时间
  /// @return 放行时为空；被限时是还要等的时长
  Optional<Duration> acquire(long userId, String clientIp, Instant now);

  /// 只计数不判：注册的首封邮件用。只计冷却和两个按账号的窗口，不计每 IP 维度（每 IP 维度只管重发）。
  ///
  /// @param userId 用户 ID
  /// @param now 当前时间
  void markSent(long userId, Instant now);
}
