package dev.patra.analytics.simulator.truth;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/// 运行中的计数（线程安全），结束时汇成 `RunSummary`。
public final class RunTally {

  private final LongAdder delivered = new LongAdder();
  private final LongAdder resent = new LongAdder();
  private final LongAdder failed = new LongAdder();
  private final LongAdder uncertain = new LongAdder();
  private final Map<String, LongAdder> sessions = new ConcurrentHashMap<>();
  private final Map<String, LongAdder> bots = new ConcurrentHashMap<>();
  private final Map<String, LongAdder> days = new ConcurrentHashMap<>();

  /// 送达若干条（不含重发）。
  ///
  /// @param day 日期（`yyyy-MM-dd`）
  /// @param count 条数
  public void delivered(String day, long count) {
    delivered.add(count);
    days.computeIfAbsent(day, d -> new LongAdder()).add(count);
  }

  /// 重发若干条。
  ///
  /// @param count 条数
  public void resent(long count) {
    resent.add(count);
  }

  /// 若干条最终没送达。
  ///
  /// @param count 条数
  public void failed(long count) {
    failed.add(count);
  }

  /// 若干条结果不明。
  ///
  /// @param count 条数
  public void uncertain(long count) {
    uncertain.add(count);
  }

  /// 产生了一个会话。
  ///
  /// @param kind 会话身份
  public void session(String kind) {
    sessions.computeIfAbsent(kind, k -> new LongAdder()).increment();
  }

  /// 标了一条爬虫事件。
  ///
  /// @param reason 原因
  public void botEvent(String reason) {
    bots.computeIfAbsent(reason, r -> new LongAdder()).increment();
  }

  /// 当前送达条数。
  ///
  /// @return 条数
  public long deliveredEvents() {
    return delivered.sum();
  }

  /// 汇成摘要。
  ///
  /// @param mode 模式
  /// @param run 运行号
  /// @param seed 种子
  /// @param fingerprint 参数指纹
  /// @return 摘要
  public RunSummary summary(String mode, int run, long seed, String fingerprint) {
    return RunSummary.builder()
        .mode(mode)
        .run(run)
        .seed(seed)
        .fingerprint(fingerprint)
        .deliveredEvents(delivered.sum())
        .resentEvents(resent.sum())
        .failedEvents(failed.sum())
        .uncertainEvents(uncertain.sum())
        .sessionsByKind(sums(sessions))
        .botEventsByReason(sums(bots))
        .eventsByDay(sums(days))
        .build();
  }

  /// 把计数器表转成数值表。
  ///
  /// @param adders 计数器
  /// @return 数值
  private static Map<String, Long> sums(Map<String, LongAdder> adders) {
    Map<String, Long> values = new TreeMap<>();
    adders.forEach((key, adder) -> values.put(key, adder.sum()));
    return values;
  }
}
