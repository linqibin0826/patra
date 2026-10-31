package dev.patra.notification.domain.mail.model.vo;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/// 每日汇总的时间窗口 `[from, to)`，按提醒写入信箱的时刻算（spec 5.2）。
///
/// @param from 左端（含）
/// @param to 右端（不含）
public record DigestWindow(Instant from, Instant to) {

  /// 窗口长度。
  public static final Duration LENGTH = Duration.ofHours(24);

  /// 每天汇总的钟点（北京时间）。
  private static final LocalTime DIGEST_TIME = LocalTime.of(8, 0);

  /// 校验非空。
  public DigestWindow {
    Objects.requireNonNull(from, "from 不能为 null");
    Objects.requireNonNull(to, "to 不能为 null");
  }

  /// 以 `end` 为右端、往前 24 小时的窗口。
  ///
  /// @param end 右端
  /// @return 窗口
  public static DigestWindow endingAt(Instant end) {
    return new DigestWindow(end.minus(LENGTH), end);
  }

  /// 定时汇总的右端：`now` 所在那天的 8:00；早于 8:00 时取前一天的 8:00。
  ///
  /// @param now 此刻
  /// @param zone 汇总钟点所在的时区（北京时间）
  /// @return 右端
  public static Instant scheduledEnd(Instant now, ZoneId zone) {
    ZonedDateTime local = now.atZone(zone);
    ZonedDateTime today = local.toLocalDate().atTime(DIGEST_TIME).atZone(zone);
    return (local.isBefore(today) ? today.minusDays(1) : today).toInstant();
  }

  /// 手动验收时的右端：此刻截到分钟。
  ///
  /// @param now 此刻
  /// @return 右端
  public static Instant manualEnd(Instant now) {
    return now.truncatedTo(ChronoUnit.MINUTES);
  }
}
