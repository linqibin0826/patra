package dev.patra.analytics.app.history.usecase.purge;

/// 过期清理删掉的行数。
///
/// @param historyRows 历史
/// @param deletionRows 删除记录
public record PurgeResult(int historyRows, int deletionRows) {

  /// 构造。
  ///
  /// @param historyRows 历史
  /// @param deletionRows 删除记录
  /// @return 结果
  public static PurgeResult of(int historyRows, int deletionRows) {
    return new PurgeResult(historyRows, deletionRows);
  }
}
