package dev.patra.notification.domain.inbox.model.read;

/// 三个标签各自的未读数。铃铛只数回复和 @（PAP-140），有帮助只在自己的标签上显示。
///
/// @param replies 「回复我的」
/// @param mentions 「@ 我的」
/// @param helpful 「有帮助」
public record UnreadCounts(int replies, int mentions, int helpful) {

  /// 构造。
  ///
  /// @param replies 「回复我的」
  /// @param mentions 「@ 我的」
  /// @param helpful 「有帮助」
  /// @return 未读数
  public static UnreadCounts of(int replies, int mentions, int helpful) {
    return new UnreadCounts(replies, mentions, helpful);
  }

  /// 铃铛上的数。
  ///
  /// @return 回复 + @，不含有帮助
  public int total() {
    return replies + mentions;
  }

  /// 未读数接口的 ETag：三个数都在里面，任何一个变了浏览器都会拿到新值。
  ///
  /// @return 带双引号的强 ETag，如 `"2-1-0"`
  public String etag() {
    return "\"" + replies + "-" + mentions + "-" + helpful + "\"";
  }
}
