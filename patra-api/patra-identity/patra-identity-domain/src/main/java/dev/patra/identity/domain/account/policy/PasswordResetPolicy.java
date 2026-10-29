package dev.patra.identity.domain.account.policy;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/// 找回密码的限流参数：申请按邮箱冷却、按邮箱每天、按 IP 每小时；提交按 IP 每分钟。
///
/// @param cooldown 同邮箱两次申请的最短间隔
/// @param perEmailDaily 同邮箱每天的申请上限
/// @param perIpHourly 同 IP 每小时的申请上限
/// @param submitPerIpMinutely 同 IP 每分钟的提交上限
public record PasswordResetPolicy(
    Duration cooldown, int perEmailDaily, int perIpHourly, int submitPerIpMinutely) {

  /// 小时窗口的长度。
  public static final Duration HOUR_WINDOW = Duration.ofHours(1);

  /// 天窗口的长度。
  public static final Duration DAY_WINDOW = Duration.ofHours(24);

  /// 分钟窗口的长度。
  public static final Duration MINUTE_WINDOW = Duration.ofMinutes(1);

  /// 冷却必须为正，三个上限至少为 1。
  public PasswordResetPolicy {
    Objects.requireNonNull(cooldown, "cooldown 不能为 null");
    if (cooldown.isZero() || cooldown.isNegative()) {
      throw new IllegalArgumentException("cooldown 必须是正数，实际值: " + cooldown);
    }
    requireAtLeastOne(perEmailDaily, "perEmailDaily");
    requireAtLeastOne(perIpHourly, "perIpHourly");
    requireAtLeastOne(submitPerIpMinutely, "submitPerIpMinutely");
  }

  /// 创建规则。
  ///
  /// @param cooldown 同邮箱两次申请的最短间隔
  /// @param perEmailDaily 同邮箱每天的申请上限
  /// @param perIpHourly 同 IP 每小时的申请上限
  /// @param submitPerIpMinutely 同 IP 每分钟的提交上限
  /// @return 规则
  public static PasswordResetPolicy of(
      Duration cooldown, int perEmailDaily, int perIpHourly, int submitPerIpMinutely) {
    return new PasswordResetPolicy(cooldown, perEmailDaily, perIpHourly, submitPerIpMinutely);
  }

  /// 这次申请之后，下次最早可以再申请的时间。
  ///
  /// @param now 这次申请的时间
  /// @return `now + cooldown`
  public Instant nextRequestAt(Instant now) {
    return Objects.requireNonNull(now, "now 不能为 null").plus(cooldown);
  }

  /// 校验上限至少为 1。
  ///
  /// @param limit 上限
  /// @param name 参数名，进报错信息
  private static void requireAtLeastOne(int limit, String name) {
    if (limit < 1) {
      throw new IllegalArgumentException(name + " 至少为 1，实际值: " + limit);
    }
  }
}
