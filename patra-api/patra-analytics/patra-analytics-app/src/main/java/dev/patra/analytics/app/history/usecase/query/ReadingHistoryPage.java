package dev.patra.analytics.app.history.usecase.query;

import dev.patra.analytics.domain.history.model.vo.ReadingEntry;
import java.util.List;

/// 我的历史的一页。
///
/// @param recordingEnabled 阅读记录是否开着
/// @param items 这一页，最近打开的在前
/// @param nextCursor 下一页的游标；到底了为 `null`
public record ReadingHistoryPage(
    boolean recordingEnabled, List<ReadingEntry> items, String nextCursor) {

  /// 防御性拷贝。
  public ReadingHistoryPage {
    items = List.copyOf(items);
  }

  /// 构造。
  ///
  /// @param recordingEnabled 阅读记录是否开着
  /// @param items 这一页
  /// @param nextCursor 下一页的游标，可空
  /// @return 一页
  public static ReadingHistoryPage of(
      boolean recordingEnabled, List<ReadingEntry> items, String nextCursor) {
    return new ReadingHistoryPage(recordingEnabled, items, nextCursor);
  }
}
