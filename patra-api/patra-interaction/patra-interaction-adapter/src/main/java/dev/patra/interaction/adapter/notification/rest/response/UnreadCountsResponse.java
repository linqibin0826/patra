package dev.patra.interaction.adapter.notification.rest.response;

import dev.patra.interaction.domain.notification.model.read.UnreadCounts;

/// 未读数：总数与两个标签各自的数。
///
/// @param total 总数
/// @param replies 「回复我的」
/// @param mentions 「@ 我的」
public record UnreadCountsResponse(int total, int replies, int mentions) {

  /// 转换。
  ///
  /// @param counts 未读数
  /// @return 响应
  public static UnreadCountsResponse from(UnreadCounts counts) {
    return new UnreadCountsResponse(counts.total(), counts.replies(), counts.mentions());
  }
}
