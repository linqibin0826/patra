package dev.patra.analytics.app.insight.usecase.hot;

/// 一次热读重算的结果。
///
/// @param ranked 上榜篇数
/// @param backfill 最新收录补位篇数
public record RefreshHotReadingResult(int ranked, int backfill) {

  /// 构造。
  ///
  /// @param ranked 上榜篇数
  /// @param backfill 补位篇数
  /// @return 结果
  public static RefreshHotReadingResult of(int ranked, int backfill) {
    return new RefreshHotReadingResult(ranked, backfill);
  }
}
