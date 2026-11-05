package dev.patra.analytics.simulator.backfill;

import java.time.LocalDate;
import java.util.Iterator;
import java.util.function.Supplier;

/// 灌历史写入 `events` 表的出口。
public interface EventSink {

  /// 整批写入一次（`JSONEachRow`，一行一条富化事件）。
  ///
  /// @param lines 行的提供者；重试时会再调一次，必须给出同样的行
  /// @param token 批次去重令牌
  /// @throws DiskFullException ClickHouse 磁盘到了红线
  void insert(Supplier<Iterator<String>> lines, String token);

  /// 一天写完后的回调（如打印活跃片段数）。
  ///
  /// @param day 日期
  default void afterDay(LocalDate day) {}
}
