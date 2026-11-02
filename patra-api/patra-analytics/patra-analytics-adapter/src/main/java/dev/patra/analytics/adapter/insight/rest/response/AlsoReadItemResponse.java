package dev.patra.analytics.adapter.insight.rest.response;

import dev.patra.analytics.domain.insight.model.vo.AlsoReadItem;

/// 看过还看了的一篇。
///
/// @param articleId 文献 ID（序列化成字符串）
/// @param source `CO_READ`、`SAME_MESH` 或 `SAME_VENUE`
/// @param meshTerm 命中的主要主题词原文；仅 `SAME_MESH`，其余为 null
/// @param venueName 期刊全称；仅 `SAME_VENUE`，其余为 null
public record AlsoReadItemResponse(
    Long articleId, String source, String meshTerm, String venueName) {

  /// 转换。
  ///
  /// @param item 一篇
  /// @return 响应
  public static AlsoReadItemResponse from(AlsoReadItem item) {
    return new AlsoReadItemResponse(
        item.articleId(), item.source().name(), item.meshTerm(), item.venueName());
  }
}
