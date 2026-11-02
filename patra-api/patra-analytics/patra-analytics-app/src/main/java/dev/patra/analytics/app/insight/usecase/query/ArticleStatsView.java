package dev.patra.analytics.app.insight.usecase.query;

/// 一篇文献的公开计数与是否在热读榜上。
///
/// @param articleId 文献 ID
/// @param viewers 看过的人数
/// @param fulltextReaders 读过全文的人数
/// @param hot 在当前热读榜前 100 名（补位的不算）
public record ArticleStatsView(long articleId, long viewers, long fulltextReaders, boolean hot) {

  /// 构造。
  ///
  /// @param articleId 文献 ID
  /// @param viewers 看过的人数
  /// @param fulltextReaders 读过全文的人数
  /// @param hot 是否在榜上
  /// @return 视图
  public static ArticleStatsView of(
      long articleId, long viewers, long fulltextReaders, boolean hot) {
    return new ArticleStatsView(articleId, viewers, fulltextReaders, hot);
  }
}
