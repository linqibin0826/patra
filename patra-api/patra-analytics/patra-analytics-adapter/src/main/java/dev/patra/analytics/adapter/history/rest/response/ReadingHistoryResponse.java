package dev.patra.analytics.adapter.history.rest.response;

import dev.patra.analytics.app.history.usecase.query.ReadingHistoryPage;
import java.util.List;

/// 我的历史的一页。
///
/// @param recordingEnabled 阅读记录是否开着
/// @param items 这一页，最近打开的在前
/// @param nextCursor 下一页的游标；到底了为 `null`
public record ReadingHistoryResponse(
    boolean recordingEnabled, List<ReadingEntryResponse> items, String nextCursor) {

  /// 防御性拷贝。
  public ReadingHistoryResponse {
    items = List.copyOf(items);
  }

  /// 转换。
  ///
  /// @param page 一页
  /// @return 响应
  public static ReadingHistoryResponse from(ReadingHistoryPage page) {
    return new ReadingHistoryResponse(
        page.recordingEnabled(),
        page.items().stream().map(ReadingEntryResponse::from).toList(),
        page.nextCursor());
  }
}
