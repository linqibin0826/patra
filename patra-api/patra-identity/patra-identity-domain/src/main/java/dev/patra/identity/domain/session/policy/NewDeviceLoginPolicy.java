package dev.patra.identity.domain.session.policy;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/// 新设备登录提醒的规则（PAP-99 设计第 12.2 节）：设备标识在回看窗口内没「见过」才算新设备，
/// 同一账号同一设备两封提醒至少隔一个冷却期。
///
/// @param lookback 回看窗口，180 天
/// @param cooldown 提醒冷却，24 小时
public record NewDeviceLoginPolicy(Duration lookback, Duration cooldown) {

  /// 两个时长都必须是正数。
  public NewDeviceLoginPolicy {
    Objects.requireNonNull(lookback, "lookback 不能为 null");
    Objects.requireNonNull(cooldown, "cooldown 不能为 null");
    if (lookback.isZero() || lookback.isNegative()) {
      throw new IllegalArgumentException("lookback 必须是正数，实际值: " + lookback);
    }
    if (cooldown.isZero() || cooldown.isNegative()) {
      throw new IllegalArgumentException("cooldown 必须是正数，实际值: " + cooldown);
    }
  }

  /// 创建规则。
  ///
  /// @param lookback 回看窗口
  /// @param cooldown 提醒冷却
  /// @return 规则
  public static NewDeviceLoginPolicy of(Duration lookback, Duration cooldown) {
    return new NewDeviceLoginPolicy(lookback, cooldown);
  }

  /// 回看窗口的起点。
  ///
  /// @param now 当前时间
  /// @return `now - lookback`
  public Instant since(Instant now) {
    return now.minus(lookback);
  }
}
