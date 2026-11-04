package dev.patra.identity.domain.account.policy;

import java.time.Duration;
import java.util.Objects;
import lombok.Builder;

/// 第三方首次注册的参数：待补填记录与验证码的有效期、错误次数上限、发码限流。
///
/// @param pendingTtl 待补填记录的有效期
/// @param codeTtl 验证码的有效期
/// @param maxCodeAttempts 一个验证码最多错几次
/// @param resendCooldown 同邮箱两次发码的最短间隔
/// @param perEmailHourly 同邮箱每小时的发码上限
/// @param perIpHourly 同 IP 每小时的发码上限
@Builder
public record ProviderRegistrationPolicy(
    Duration pendingTtl,
    Duration codeTtl,
    int maxCodeAttempts,
    Duration resendCooldown,
    int perEmailHourly,
    int perIpHourly) {

  /// 小时窗口的长度。
  public static final Duration HOUR_WINDOW = Duration.ofHours(1);

  /// 时长必须为正，上限至少为 1。
  public ProviderRegistrationPolicy {
    requirePositive(pendingTtl, "pendingTtl");
    requirePositive(codeTtl, "codeTtl");
    requirePositive(resendCooldown, "resendCooldown");
    requireAtLeastOne(maxCodeAttempts, "maxCodeAttempts");
    requireAtLeastOne(perEmailHourly, "perEmailHourly");
    requireAtLeastOne(perIpHourly, "perIpHourly");
  }

  /// 校验时长为正。
  ///
  /// @param value 时长
  /// @param name 参数名，进报错信息
  private static void requirePositive(Duration value, String name) {
    Objects.requireNonNull(value, name + " 不能为 null");
    if (value.isZero() || value.isNegative()) {
      throw new IllegalArgumentException(name + " 必须是正数，实际值: " + value);
    }
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
