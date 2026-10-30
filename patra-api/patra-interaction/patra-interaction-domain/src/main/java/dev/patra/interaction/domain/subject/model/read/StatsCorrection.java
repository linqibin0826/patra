package dev.patra.interaction.domain.subject.model.read;

/// 按明细重算改掉的一行统计：改之前与改之后的值（没有统计行算 0）。
///
/// @param before 改之前
/// @param after 改之后（按明细数出来的）
public record StatsCorrection(long before, long after) {

  /// 创建。
  ///
  /// @param before 改之前
  /// @param after 改之后
  /// @return 修正
  public static StatsCorrection of(long before, long after) {
    return new StatsCorrection(before, after);
  }
}
