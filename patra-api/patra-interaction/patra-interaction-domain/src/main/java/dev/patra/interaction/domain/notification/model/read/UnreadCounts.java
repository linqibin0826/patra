package dev.patra.interaction.domain.notification.model.read;

/// 未读数：两个标签各自的数。
///
/// @param replies 「回复我的」未读数
/// @param mentions 「@ 我的」未读数
public record UnreadCounts(int replies, int mentions) {

  /// 构造。
  ///
  /// @param replies 「回复我的」未读数
  /// @param mentions 「@ 我的」未读数
  /// @return 未读数
  public static UnreadCounts of(int replies, int mentions) {
    return new UnreadCounts(replies, mentions);
  }

  /// 总未读数。
  ///
  /// @return 两个标签之和
  public int total() {
    return replies + mentions;
  }
}
