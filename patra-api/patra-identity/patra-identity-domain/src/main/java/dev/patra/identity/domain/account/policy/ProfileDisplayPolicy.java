package dev.patra.identity.domain.account.policy;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Objects;

/// 公开主页的展示规则：加入时间只给到月，按 identity 的展示时区（与安全通知邮件同一个时区）换算。
///
/// @param zone 展示时区
public record ProfileDisplayPolicy(ZoneId zone) {

  /// 校验非空。
  public ProfileDisplayPolicy {
    Objects.requireNonNull(zone, "zone 不能为 null");
  }

  /// 工厂方法。
  ///
  /// @param zone 展示时区
  /// @return 规则
  public static ProfileDisplayPolicy of(ZoneId zone) {
    return new ProfileDisplayPolicy(zone);
  }

  /// 加入的年月。
  ///
  /// @param joinedAt 注册时间
  /// @return 展示时区下的年月
  public YearMonth joinedMonth(Instant joinedAt) {
    return YearMonth.from(joinedAt.atZone(zone));
  }
}
