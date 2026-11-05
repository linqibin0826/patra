package dev.patra.analytics.adapter.insight.rest.response;

import dev.patra.analytics.domain.insight.model.vo.HotReadingSummary;
import java.time.Instant;
import java.util.List;

/// 首页热读：一页最多 14 篇，上榜的在前、补位的在后。
///
/// @param computedAt 快照的计算时间；任务从没跑过为 null
/// @param rankedTotal 榜上一共几篇（0 ~ 100）
/// @param items 这一页
public record HotReadingResponse(
    Instant computedAt, int rankedTotal, List<HotReadingItemResponse> items) {

  /// 防御性拷贝。
  public HotReadingResponse {
    items = List.copyOf(items);
  }

  /// 转换。
  ///
  /// @param summary 热读页
  /// @return 响应
  public static HotReadingResponse from(HotReadingSummary summary) {
    return new HotReadingResponse(
        summary.computedAt(),
        summary.rankedTotal(),
        summary.items().stream().map(HotReadingItemResponse::from).toList());
  }
}
