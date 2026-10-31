package dev.patra.notification.domain.mail.policy;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/// 邮件里的时间写法（spec 8.4）：一律写北京时间的绝对时间，邮件常常隔很久才打开。
///
/// @param zone 显示时区
public record ReminderTimeText(ZoneId zone) {

  /// 带日期：`10 月 30 日 15:02`。
  private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("M' 月 'd' 日 'HH:mm");

  /// 只写钟点：`15:02`。
  private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

  /// 只写日期：`10 月 31 日`。
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("M' 月 'd' 日'");

  /// 校验非空。
  public ReminderTimeText {
    Objects.requireNonNull(zone, "zone 不能为 null");
  }

  /// 构造。
  ///
  /// @param zone 显示时区
  /// @return 时间写法
  public static ReminderTimeText of(ZoneId zone) {
    return new ReminderTimeText(zone);
  }

  /// 带日期的写法。
  ///
  /// @param instant 时刻
  /// @return `10 月 30 日 15:02`
  public String full(Instant instant) {
    return FULL.format(instant.atZone(zone));
  }

  /// 只写钟点。
  ///
  /// @param instant 时刻
  /// @return `15:02`
  public String clock(Instant instant) {
    return CLOCK.format(instant.atZone(zone));
  }

  /// 只写日期。
  ///
  /// @param instant 时刻
  /// @return `10 月 31 日`
  public String date(Instant instant) {
    return DATE.format(instant.atZone(zone));
  }

  /// 两个时刻是不是同一天（按显示时区）。
  ///
  /// @param a 时刻
  /// @param b 时刻
  /// @return 同一天为真
  public boolean sameDay(Instant a, Instant b) {
    return a.atZone(zone).toLocalDate().equals(b.atZone(zone).toLocalDate());
  }
}
