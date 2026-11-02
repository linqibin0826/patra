package dev.patra.analytics.adapter.insight.rest.response;

import dev.patra.analytics.domain.insight.model.vo.HotReadingEntry;

/// 热读页的一篇。
///
/// @param articleId 文献 ID（序列化成字符串）
/// @param source `RANKED`（上榜）或 `LATEST`（最新收录补位）
/// @param rank 名次，1 起；补位为 null
public record HotReadingItemResponse(Long articleId, String source, Integer rank) {

  /// 转换。
  ///
  /// @param entry 快照的一行
  /// @return 响应
  public static HotReadingItemResponse from(HotReadingEntry entry) {
    return entry.ranked()
        ? new HotReadingItemResponse(entry.articleId(), "RANKED", entry.position())
        : new HotReadingItemResponse(entry.articleId(), "LATEST", null);
  }
}
