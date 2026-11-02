package dev.patra.analytics.domain.history.model.vo;

import java.time.Instant;
import java.util.Objects;

/// 要写进历史的一次打开：合并后每人每篇一条。
///
/// @param userId 用户 ID
/// @param articleId 文献 ID
/// @param readAt 打开的时刻
/// @param fulltext 是否读了全文
public record ReadingRecord(long userId, long articleId, Instant readAt, boolean fulltext) {

  /// 时间必填。
  public ReadingRecord {
    Objects.requireNonNull(readAt, "readAt");
  }

  /// 创建一条。
  ///
  /// @param userId 用户 ID
  /// @param articleId 文献 ID
  /// @param readAt 打开的时刻
  /// @param fulltext 是否读了全文
  /// @return 记录
  public static ReadingRecord of(long userId, long articleId, Instant readAt, boolean fulltext) {
    return new ReadingRecord(userId, articleId, readAt, fulltext);
  }

  /// 这一条的键。
  ///
  /// @return（用户，文献）
  public ReadingKey key() {
    return ReadingKey.of(userId, articleId);
  }
}
