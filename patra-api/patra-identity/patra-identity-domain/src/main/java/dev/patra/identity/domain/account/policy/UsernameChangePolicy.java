package dev.patra.identity.domain.account.policy;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/// 改名规则：两次改名的最短间隔（冷却），以及旧名保留多久。
///
/// @param cooldown 冷却时长，默认 30 天
/// @param reservationPeriod 旧名保留时长，默认 90 天
public record UsernameChangePolicy(Duration cooldown, Duration reservationPeriod) {

  /// 两个时长都必须是正数。
  public UsernameChangePolicy {
    requirePositive(cooldown, "cooldown");
    requirePositive(reservationPeriod, "reservationPeriod");
  }

  /// 创建规则。
  ///
  /// @param cooldown 冷却时长
  /// @param reservationPeriod 旧名保留时长
  /// @return 规则
  public static UsernameChangePolicy of(Duration cooldown, Duration reservationPeriod) {
    return new UsernameChangePolicy(cooldown, reservationPeriod);
  }

  /// 还要等多久才能再改名。从没改过名，或冷却已到期（含到期当刻），返回空。
  ///
  /// @param changedAt 最近一次改名时间，可以为 `null`
  /// @param now 当前时间
  /// @return 剩余的冷却时长；不用等时为空
  public Optional<Duration> remainingCooldown(Instant changedAt, Instant now) {
    Objects.requireNonNull(now, "now 不能为 null");
    if (changedAt == null) {
      return Optional.empty();
    }
    Instant next = changedAt.plus(cooldown);
    if (!now.isBefore(next)) {
      return Optional.empty();
    }
    return Optional.of(Duration.between(now, next));
  }

  /// 下次可以改名的时间。
  ///
  /// @param changedAt 最近一次改名时间，可以为 `null`
  /// @return `changedAt + cooldown`；从没改过名时为 `null`
  public Instant nextChangeAt(Instant changedAt) {
    return changedAt == null ? null : changedAt.plus(cooldown);
  }

  /// 校验时长为正数。
  ///
  /// @param duration 时长
  /// @param name 参数名，进报错信息
  private static void requirePositive(Duration duration, String name) {
    Objects.requireNonNull(duration, name + " 不能为 null");
    if (duration.isZero() || duration.isNegative()) {
      throw new IllegalArgumentException(name + " 必须是正数，实际值: " + duration);
    }
  }
}
