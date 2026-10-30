package dev.patra.interaction.app.subject.usecase.recount;

/// 按明细重算里一项计数的结果。
///
/// @param counter 计数名，如 `collection.collector_count`
/// @param corrected 真改了的行数（差异条数）
public record CounterResult(String counter, int corrected) {

  /// 创建。
  ///
  /// @param counter 计数名
  /// @param corrected 修正的行数
  /// @return 结果
  public static CounterResult of(String counter, int corrected) {
    return new CounterResult(counter, corrected);
  }
}
