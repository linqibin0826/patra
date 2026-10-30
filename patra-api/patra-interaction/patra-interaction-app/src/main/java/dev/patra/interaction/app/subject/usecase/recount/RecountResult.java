package dev.patra.interaction.app.subject.usecase.recount;

import java.util.List;
import java.util.stream.Collectors;

/// 按明细重算的结果：每项计数一行，按计数名排序（由 Handler 排好）。
///
/// @param counters 各项计数的结果
public record RecountResult(List<CounterResult> counters) {

  /// 防御性拷贝。
  public RecountResult {
    counters = List.copyOf(counters);
  }

  /// 创建。
  ///
  /// @param counters 各项计数的结果，按计数名排序
  /// @return 结果
  public static RecountResult of(List<CounterResult> counters) {
    return new RecountResult(counters);
  }

  /// 一共修正的行数（差异条数）。
  ///
  /// @return 行数
  public int corrected() {
    return counters.stream().mapToInt(CounterResult::corrected).sum();
  }

  /// 各项的修正行数，写进日志与任务回写：`{计数名=行数, …}`。
  ///
  /// @return 摘要
  public String summary() {
    return counters.stream()
        .map(c -> c.counter() + "=" + c.corrected())
        .collect(Collectors.joining(", ", "{", "}"));
  }
}
