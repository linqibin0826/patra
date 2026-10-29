package dev.patra.identity.domain.account.policy;

import java.time.Duration;
import java.util.Objects;

/// 注销规则：冷静期多长、到期前多久发提醒。
///
/// @param coolingPeriod 冷静期，默认 15 天
/// @param reminderLead 到期前多久提醒，默认 3 天；必须短于冷静期
public record AccountDeletionPolicy(Duration coolingPeriod, Duration reminderLead) {

  /// 两个时长都必须是正数，提前量必须短于冷静期（否则发起即提醒，没有意义）。
  public AccountDeletionPolicy {
    requirePositive(coolingPeriod, "coolingPeriod");
    requirePositive(reminderLead, "reminderLead");
    if (reminderLead.compareTo(coolingPeriod) >= 0) {
      throw new IllegalArgumentException("reminderLead 必须短于 coolingPeriod");
    }
  }

  /// 创建规则。
  ///
  /// @param coolingPeriod 冷静期
  /// @param reminderLead 到期前多久提醒
  /// @return 规则
  public static AccountDeletionPolicy of(Duration coolingPeriod, Duration reminderLead) {
    return new AccountDeletionPolicy(coolingPeriod, reminderLead);
  }

  /// 时长必须非空且为正。
  ///
  /// @param value 时长
  /// @param name 名字，进异常信息
  private static void requirePositive(Duration value, String name) {
    Objects.requireNonNull(value, name + " 不能为 null");
    if (value.isZero() || value.isNegative()) {
      throw new IllegalArgumentException(name + " 必须是正数");
    }
  }
}
