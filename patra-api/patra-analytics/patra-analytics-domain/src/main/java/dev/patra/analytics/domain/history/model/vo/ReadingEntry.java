package dev.patra.analytics.domain.history.model.vo;

import java.time.Instant;
import java.util.Objects;

/// 我的历史里的一行。
///
/// @param articleId 文献 ID
/// @param lastReadAt 最近一次打开的时间
/// @param fulltextRead 是否读过全文
public record ReadingEntry(long articleId, Instant lastReadAt, boolean fulltextRead) {

  /// 时间必填。
  public ReadingEntry {
    Objects.requireNonNull(lastReadAt, "lastReadAt");
  }

  /// 创建一行。
  ///
  /// @param articleId 文献 ID
  /// @param lastReadAt 最近一次打开的时间
  /// @param fulltextRead 是否读过全文
  /// @return 一行
  public static ReadingEntry of(long articleId, Instant lastReadAt, boolean fulltextRead) {
    return new ReadingEntry(articleId, lastReadAt, fulltextRead);
  }

  /// 指向这一行的游标。
  ///
  /// @return 游标
  public HistoryCursor cursor() {
    return HistoryCursor.of(lastReadAt, articleId);
  }
}
