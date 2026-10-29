package dev.patra.catalog.app.fulltext.usecase.fetch;

/// 一次抓取运行的统计。
///
/// @param processed 处理篇数
/// @param fetched 抓到篇数
/// @param unavailable 不可用篇数
/// @param failed 失败篇数
/// @author linqibin
/// @since 0.11.0
public record FullTextFetchRunStats(int processed, int fetched, int unavailable, int failed) {

  /// 创建统计。
  ///
  /// @param processed 处理篇数
  /// @param fetched 抓到篇数
  /// @param unavailable 不可用篇数
  /// @param failed 失败篇数
  /// @return 统计
  public static FullTextFetchRunStats of(int processed, int fetched, int unavailable, int failed) {
    return new FullTextFetchRunStats(processed, fetched, unavailable, failed);
  }

  /// 全为 0 的统计。
  ///
  /// @return 空统计
  public static FullTextFetchRunStats empty() {
    return new FullTextFetchRunStats(0, 0, 0, 0);
  }

  /// 计入一篇的结果。
  ///
  /// @param outcome 结果
  /// @return 新统计
  public FullTextFetchRunStats plus(FetchOutcome outcome) {
    return new FullTextFetchRunStats(
        processed + 1,
        fetched + (outcome == FetchOutcome.FETCHED ? 1 : 0),
        unavailable + (outcome == FetchOutcome.UNAVAILABLE ? 1 : 0),
        failed + (outcome == FetchOutcome.FAILED ? 1 : 0));
  }

  /// 一句话摘要。
  ///
  /// @return 摘要
  public String message() {
    return "全文抓取完成：处理 %d 篇，抓到 %d、不可用 %d、失败 %d".formatted(processed, fetched, unavailable, failed);
  }
}
