package dev.patra.analytics.simulator.backfill;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/// 灌历史的批次去重令牌：`backfill:{运行号}:{参数指纹}:{yyyyMMdd}:{块号 | spill | dup}`（spec §8）。
public final class BackfillTokens {

  private BackfillTokens() {}

  /// 拼令牌。
  ///
  /// @param run 运行号
  /// @param fingerprint 参数指纹
  /// @param day 这次写入的日期
  /// @param part 块号、`spill` 或 `dup`
  /// @return 令牌
  public static String of(int run, String fingerprint, LocalDate day, String part) {
    return "backfill:"
        + run
        + ":"
        + fingerprint
        + ":"
        + day.format(DateTimeFormatter.BASIC_ISO_DATE)
        + ":"
        + part;
  }
}
