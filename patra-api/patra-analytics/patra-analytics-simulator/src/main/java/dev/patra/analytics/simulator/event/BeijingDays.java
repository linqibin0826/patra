package dev.patra.analytics.simulator.event;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/// 「天」一律按北京时间切（入库设计 §5.1），与 ClickHouse 的分区和汇总表的 `day` 一致。
public final class BeijingDays {

  /// 北京时间
  public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

  private BeijingDays() {}

  /// 时刻落在北京时间的哪一天。
  ///
  /// @param instant 时刻
  /// @return 日期
  public static LocalDate of(Instant instant) {
    return LocalDate.ofInstant(instant, ZONE);
  }
}
