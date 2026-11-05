package dev.patra.analytics.app.insight.usecase.coread;

/// 一次共现重算的结果。
///
/// @param articles 有推荐的文献篇数
/// @param rows 推荐总条数
public record RefreshCoReadingResult(int articles, int rows) {

  /// 构造。
  ///
  /// @param articles 文献篇数
  /// @param rows 推荐条数
  /// @return 结果
  public static RefreshCoReadingResult of(int articles, int rows) {
    return new RefreshCoReadingResult(articles, rows);
  }
}
