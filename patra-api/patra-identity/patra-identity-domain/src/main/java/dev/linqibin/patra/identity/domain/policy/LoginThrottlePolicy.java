package dev.linqibin.patra.identity.domain.policy;

import java.time.Duration;
import java.util.Objects;

/// 登录失败限制的参数。
///
/// @param maxFailures 计数窗口内允许的失败次数，达到就上锁
/// @param window 计数窗口，从窗口内第一次失败算起
/// @param lockDuration 锁定时长
/// @param inFlightTtl 在途登记的过期时间，进程崩溃没来得及结算时用它兜底
public record LoginThrottlePolicy(
    int maxFailures, Duration window, Duration lockDuration, Duration inFlightTtl) {

  /// 校验参数。
  ///
  /// @param maxFailures 失败次数上限
  /// @param window 计数窗口
  /// @param lockDuration 锁定时长
  /// @param inFlightTtl 在途登记的过期时间
  public LoginThrottlePolicy {
    if (maxFailures < 1) {
      throw new IllegalArgumentException("maxFailures 至少为 1");
    }
    requirePositive(window, "window");
    requirePositive(lockDuration, "lockDuration");
    requirePositive(inFlightTtl, "inFlightTtl");
  }

  /// 创建参数。
  ///
  /// @param maxFailures 失败次数上限
  /// @param window 计数窗口
  /// @param lockDuration 锁定时长
  /// @param inFlightTtl 在途登记的过期时间
  /// @return 参数
  public static LoginThrottlePolicy of(
      int maxFailures, Duration window, Duration lockDuration, Duration inFlightTtl) {
    return new LoginThrottlePolicy(maxFailures, window, lockDuration, inFlightTtl);
  }

  /// 校验时长为正。
  ///
  /// @param duration 时长
  /// @param name 参数名
  private static void requirePositive(Duration duration, String name) {
    Objects.requireNonNull(duration, name + " 不能为 null");
    if (duration.isZero() || duration.isNegative()) {
      throw new IllegalArgumentException(name + " 必须为正");
    }
  }
}
