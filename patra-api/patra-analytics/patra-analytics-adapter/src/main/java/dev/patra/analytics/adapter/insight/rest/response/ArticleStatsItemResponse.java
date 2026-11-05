package dev.patra.analytics.adapter.insight.rest.response;

import dev.patra.analytics.app.insight.usecase.query.ArticleStatsView;

/// 一篇文献的公开计数。
///
/// @param articleId 文献 ID（序列化成字符串）
/// @param viewerCount 看过的人数
/// @param fulltextReaderCount 读过全文的人数
/// @param hot 在当前热读榜前 100 名（补位的不算）
public record ArticleStatsItemResponse(
    Long articleId, int viewerCount, int fulltextReaderCount, boolean hot) {

  /// 转换。
  ///
  /// @param view 查询结果
  /// @return 响应
  public static ArticleStatsItemResponse from(ArticleStatsView view) {
    return new ArticleStatsItemResponse(
        view.articleId(),
        Math.toIntExact(view.viewers()),
        Math.toIntExact(view.fulltextReaders()),
        view.hot());
  }
}
