package dev.patra.analytics.adapter.history.rest.response;

import dev.patra.analytics.domain.history.model.vo.ReadingEntry;
import java.time.Instant;

/// 我的历史里的一篇。标题、刊名由门户按 ID 调 catalog 的批量接口取。
///
/// @param articleId 文献 ID（序列化成字符串）
/// @param lastReadAt 最近一次打开的时间
/// @param fulltextRead 是否读过全文
public record ReadingEntryResponse(Long articleId, Instant lastReadAt, boolean fulltextRead) {

  /// 转换。
  ///
  /// @param entry 一行
  /// @return 响应
  public static ReadingEntryResponse from(ReadingEntry entry) {
    return new ReadingEntryResponse(entry.articleId(), entry.lastReadAt(), entry.fulltextRead());
  }
}
