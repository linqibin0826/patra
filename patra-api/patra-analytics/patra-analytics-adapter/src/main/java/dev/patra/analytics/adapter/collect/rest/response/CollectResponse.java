package dev.patra.analytics.adapter.collect.rest.response;

/// 采集结果。
///
/// @param accepted 写进 `events.raw` 的条数
/// @param rejected 写进 `events.dlq` 的条数
public record CollectResponse(int accepted, int rejected) {

  /// 创建响应。
  ///
  /// @param accepted 合格条数
  /// @param rejected 不合格条数
  /// @return 响应
  public static CollectResponse of(int accepted, int rejected) {
    return new CollectResponse(accepted, rejected);
  }
}
