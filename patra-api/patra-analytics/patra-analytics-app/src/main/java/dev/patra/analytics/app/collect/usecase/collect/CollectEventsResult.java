package dev.patra.analytics.app.collect.usecase.collect;

/// 一批事件的采集结果。
///
/// @param accepted 写进 `events.raw` 的条数
/// @param rejected 写进 `events.dlq` 的条数
public record CollectEventsResult(int accepted, int rejected) {

  /// 创建结果。
  ///
  /// @param accepted 合格条数
  /// @param rejected 不合格条数
  /// @return 结果
  public static CollectEventsResult of(int accepted, int rejected) {
    return new CollectEventsResult(accepted, rejected);
  }
}
