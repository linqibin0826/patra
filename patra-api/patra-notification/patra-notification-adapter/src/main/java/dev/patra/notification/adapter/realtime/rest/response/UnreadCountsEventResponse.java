package dev.patra.notification.adapter.realtime.rest.response;

import dev.patra.notification.domain.inbox.model.read.UnreadCounts;

/// 推送事件 `unread-count` 的数据：与 `GET /me/notifications/unread-count` 的响应体同形（门户用同一个解析函数）。
///
/// @param total 铃铛：回复 + @，不含有帮助
/// @param replies 「回复我的」
/// @param mentions 「@ 我的」
/// @param helpful 「有帮助」
public record UnreadCountsEventResponse(int total, int replies, int mentions, int helpful) {

  /// 转换。
  ///
  /// @param counts 未读数
  /// @return 事件数据
  public static UnreadCountsEventResponse from(UnreadCounts counts) {
    return new UnreadCountsEventResponse(
        counts.total(), counts.replies(), counts.mentions(), counts.helpful());
  }
}
