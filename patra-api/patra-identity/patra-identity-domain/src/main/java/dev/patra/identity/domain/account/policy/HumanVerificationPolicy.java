package dev.patra.identity.domain.account.policy;

import dev.patra.identity.domain.account.model.enums.HumanVerificationProvider;
import java.time.Duration;
import java.util.Objects;

/// 人机验证的参数：当前厂商和登录触发验证的门槛。
///
/// @param provider 当前厂商
/// @param emailFailureThreshold 同一邮箱失败多少次后要验证
/// @param ipFailureThreshold 同一 IP 失败多少次后要验证
/// @param ipFailureWindow IP 失败计数的窗口，从第一次失败算起
public record HumanVerificationPolicy(
    HumanVerificationProvider provider,
    int emailFailureThreshold,
    int ipFailureThreshold,
    Duration ipFailureWindow) {

  /// 校验参数。
  ///
  /// @param provider 当前厂商
  /// @param emailFailureThreshold 邮箱门槛
  /// @param ipFailureThreshold IP 门槛
  /// @param ipFailureWindow IP 窗口
  public HumanVerificationPolicy {
    Objects.requireNonNull(provider, "provider 不能为 null");
    Objects.requireNonNull(ipFailureWindow, "ipFailureWindow 不能为 null");
    if (emailFailureThreshold < 1 || ipFailureThreshold < 1) {
      throw new IllegalArgumentException("门槛至少为 1");
    }
    if (ipFailureWindow.isZero() || ipFailureWindow.isNegative()) {
      throw new IllegalArgumentException("ipFailureWindow 必须为正");
    }
  }

  /// 创建参数。
  ///
  /// @param provider 当前厂商
  /// @param emailFailureThreshold 邮箱门槛
  /// @param ipFailureThreshold IP 门槛
  /// @param ipFailureWindow IP 窗口
  /// @return 参数
  public static HumanVerificationPolicy of(
      HumanVerificationProvider provider,
      int emailFailureThreshold,
      int ipFailureThreshold,
      Duration ipFailureWindow) {
    return new HumanVerificationPolicy(
        provider, emailFailureThreshold, ipFailureThreshold, ipFailureWindow);
  }

  /// 判断这次登录要不要先过验证：两个门槛满足任一个就要。
  ///
  /// @param emailFailures 这个邮箱的失败次数
  /// @param ipFailures 这个 IP 的失败次数
  /// @return 要验证时为 `true`
  public boolean requiresVerification(int emailFailures, int ipFailures) {
    return emailFailures >= emailFailureThreshold || ipFailures >= ipFailureThreshold;
  }
}
