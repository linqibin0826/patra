package dev.patra.analytics.domain.history.model.vo;

/// 历史一行的键：（用户，文献）。
///
/// @param userId 用户 ID
/// @param articleId 文献 ID
public record ReadingKey(long userId, long articleId) {

  /// 创建键。
  ///
  /// @param userId 用户 ID
  /// @param articleId 文献 ID
  /// @return 键
  public static ReadingKey of(long userId, long articleId) {
    return new ReadingKey(userId, articleId);
  }
}
