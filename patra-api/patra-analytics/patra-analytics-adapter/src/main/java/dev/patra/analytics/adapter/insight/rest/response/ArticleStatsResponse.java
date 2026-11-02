package dev.patra.analytics.adapter.insight.rest.response;

import java.util.List;

/// 批量计数：去重后按请求顺序，每篇一行。
///
/// @param items 计数
public record ArticleStatsResponse(List<ArticleStatsItemResponse> items) {

  /// 防御性拷贝。
  public ArticleStatsResponse {
    items = List.copyOf(items);
  }

  /// 构造。
  ///
  /// @param items 计数
  /// @return 响应
  public static ArticleStatsResponse of(List<ArticleStatsItemResponse> items) {
    return new ArticleStatsResponse(items);
  }
}
