package dev.patra.analytics.domain.history.policy;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

/// 阅读历史的保留与翻页规则（spec §6.1、§7.1）：历史留 12 个日历月（UTC），删除记录留 2 天。
public final class HistoryRetention {

  /// 一页的条数
  public static final int PAGE_SIZE = 30;

  /// 过期清理每批删的行数
  public static final int PURGE_BATCH = 10_000;

  /// 历史保留的月数
  private static final int HISTORY_MONTHS = 12;

  /// 删除记录保留的时长：事件时间最多比收到时刻早 24 小时，2 天后删除记录不再起作用
  private static final Duration DELETION_KEPT = Duration.ofDays(2);

  private HistoryRetention() {}

  /// 历史的过期线：早于它的行删掉、不再返回。按 UTC 日历减 12 个月，月末对不上时取那个月的最后一天。
  ///
  /// @param now 现在
  /// @return 过期线
  public static Instant historyCutoff(Instant now) {
    return now.atOffset(ZoneOffset.UTC).minusMonths(HISTORY_MONTHS).toInstant();
  }

  /// 删除记录的过期线。
  ///
  /// @param now 现在
  /// @return 过期线
  public static Instant deletionCutoff(Instant now) {
    return now.minus(DELETION_KEPT);
  }
}
