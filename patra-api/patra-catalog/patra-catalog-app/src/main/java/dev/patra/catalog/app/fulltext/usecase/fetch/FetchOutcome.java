package dev.patra.catalog.app.fulltext.usecase.fetch;

/// 一篇文献的抓取结果。
///
/// @author linqibin
/// @since 0.11.0
public enum FetchOutcome {

  /// 抓到了
  FETCHED,

  /// 不可用（含暂不可用）
  UNAVAILABLE,

  /// 抓取失败
  FAILED
}
