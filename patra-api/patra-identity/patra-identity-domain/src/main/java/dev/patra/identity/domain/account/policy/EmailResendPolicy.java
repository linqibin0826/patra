package dev.patra.identity.domain.account.policy;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/// 重发验证邮件的限流规则：两次发送的最短间隔（冷却），每账号每小时、每天的上限，每 IP 每小时的上限。
///
/// 窗口是滑动的：「任意连续一小时 ≤ 5 次」，不按自然小时切。
///
/// @param cooldown 两次发送的最短间隔，默认 60 秒
/// @param perAccountHourly 每账号每小时的发送上限，默认 5
/// @param perAccountDaily 每账号每天的发送上限，默认 10
/// @param perIpHourly 每 IP 每小时的发送上限，默认 30
public record EmailResendPolicy(
    Duration cooldown, int perAccountHourly, int perAccountDaily, int perIpHourly) {

  /// 小时窗口的长度。
  public static final Duration HOUR_WINDOW = Duration.ofHours(1);

  /// 天窗口的长度。
  public static final Duration DAY_WINDOW = Duration.ofHours(24);

  /// 冷却必须为正，三个上限至少为 1。
  public EmailResendPolicy {
    Objects.requireNonNull(cooldown, "cooldown 不能为 null");
    if (cooldown.isZero() || cooldown.isNegative()) {
      throw new IllegalArgumentException("cooldown 必须是正数，实际值: " + cooldown);
    }
    requireAtLeastOne(perAccountHourly, "perAccountHourly");
    requireAtLeastOne(perAccountDaily, "perAccountDaily");
    requireAtLeastOne(perIpHourly, "perIpHourly");
  }

  /// 创建规则。
  ///
  /// @param cooldown 两次发送的最短间隔
  /// @param perAccountHourly 每账号每小时的发送上限
  /// @param perAccountDaily 每账号每天的发送上限
  /// @param perIpHourly 每 IP 每小时的发送上限
  /// @return 规则
  public static EmailResendPolicy of(
      Duration cooldown, int perAccountHourly, int perAccountDaily, int perIpHourly) {
    return new EmailResendPolicy(cooldown, perAccountHourly, perAccountDaily, perIpHourly);
  }

  /// 这次发送之后，下次最早可以重发的时间。
  ///
  /// @param now 这次发送的时间
  /// @return `now + cooldown`
  public Instant nextResendAt(Instant now) {
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
