package dev.patra.notification.adapter.inbox.rest.response;

import dev.patra.notification.app.inbox.usecase.query.NotificationPage;
import java.util.List;

/// 提醒列表的一页。
///
/// @param items 提醒，排序时间新的在前
/// @param nextCursor 下一页的游标，不透明，原样回传；没有下一页为空
public record NotificationPageResponse(List<NotificationResponse> items, String nextCursor) {

  /// 防御性拷贝。
  public NotificationPageResponse {
    items = List.copyOf(items);
  }

  /// 转换。
  ///
  /// @param page 一页
  /// @return 响应
  public static NotificationPageResponse from(NotificationPage page) {
    return new NotificationPageResponse(
        page.items().stream().map(NotificationResponse::from).toList(), page.nextCursor());
  }
}
