package dev.patra.analytics.simulator.event;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/// 事件里的时间字符串：UTC、恰好 3 位毫秒、以 `Z` 结尾（埋点方案的 `timestamp`）。
public final class Timestamps {

  private static final DateTimeFormatter FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

  private Timestamps() {}

  /// 格式化成事件里的时间字符串，毫秒以下截掉。
  ///
  /// @param instant 时刻
  /// @return 如 `2026-11-01T08:30:10.120Z`
  public static String format(Instant instant) {
    return FORMAT.format(instant);
  }
}
