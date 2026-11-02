package dev.patra.analytics.simulator.backfill;

import dev.patra.analytics.simulator.model.ArrivalCurve;
import dev.patra.analytics.simulator.model.TrafficModel;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/// 灌历史的日程（spec §8）：最后一天（命令行默认昨天）往前若干天，每天的事件数按周内系数分配，再按平均每会话事件数换成会话数、切成块。
public final class BackfillPlan {

  /// 一块的目标行数：一次写入就是 ClickHouse 的一个数据块，令牌含义不含糊
  static final int ROWS_PER_CHUNK = 250_000;

  private final List<LocalDate> days;
  private final Map<LocalDate, Long> events;
  private final double meanEvents;
  private final int sessionsPerChunk;
  private final Map<LocalDate, Integer> firstOrdinal = new HashMap<>();

  private BackfillPlan(List<LocalDate> days, Map<LocalDate, Long> events, double meanEvents) {
    this.days = days;
    this.events = events;
    this.meanEvents = meanEvents;
    this.sessionsPerChunk = (int) Math.ceil(ROWS_PER_CHUNK / meanEvents);
    int ordinal = 0;
    for (LocalDate day : days) {
      firstOrdinal.put(day, ordinal);
      ordinal += chunks(day);
    }
  }

  /// 排日程：`endDay` 往前 `days` 天。
  ///
  /// @param params 参数
  /// @param model 流量模型
  /// @param meanEvents 平均每会话事件数（含爬虫）
  /// @return 日程
  public static BackfillPlan of(BackfillParams params, TrafficModel model, double meanEvents) {
    List<LocalDate> days =
        Stream.iterate(params.endDay().minusDays(params.days() - 1L), d -> d.plusDays(1))
            .limit(params.days())
            .toList();
    return new BackfillPlan(
        days, new ArrivalCurve(model).allocate(params.events(), days), meanEvents);
  }

  /// 各天，升序。
  ///
  /// @return 日期
  public List<LocalDate> days() {
    return days;
  }

  /// 某天的目标事件数。
  ///
  /// @param day 日期
  /// @return 条数
  public long events(LocalDate day) {
    return events.get(day);
  }

  /// 某天的会话数。
  ///
  /// @param day 日期
  /// @return 会话数
  public long sessions(LocalDate day) {
    return Math.round(events(day) / meanEvents);
  }

  /// 每块的会话数。
  ///
  /// @return 会话数
  public int sessionsPerChunk() {
    return sessionsPerChunk;
  }

  /// 某天有几块。
  ///
  /// @param day 日期
  /// @return 块数
  public int chunks(LocalDate day) {
    return (int) Math.ceil(sessions(day) / (double) sessionsPerChunk);
  }

  /// 块在整个运行里的序号（一次性设备的下标按它分段，互不重叠）。
  ///
  /// @param day 日期
  /// @param chunk 当天的块号
  /// @return 序号
  public int chunkOrdinal(LocalDate day, int chunk) {
    return firstOrdinal.get(day) + chunk;
  }
}
