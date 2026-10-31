package dev.patra.notification.adapter.inbox.rest.response;

import dev.patra.notification.domain.inbox.model.read.UnreadCounts;

/// 未读数：铃铛上的数与三个标签各自的数。实时推送（PAP-145）推的也是这个形状。
///
/// @param total 铃铛：回复 + @，不含有帮助
/// @param replies 「回复我的」
/// @param mentions 「@ 我的」
/// @param helpful 「有帮助」
public record UnreadCountsResponse(int total, int replies, int mentions, int helpful) {

  /// 转换。
  ///
  /// @param counts 未读数
  /// @return 响应
  public static UnreadCountsResponse from(UnreadCounts counts) {
    return new UnreadCountsResponse(
        counts.total(), counts.replies(), counts.mentions(), counts.helpful());
  }
}
