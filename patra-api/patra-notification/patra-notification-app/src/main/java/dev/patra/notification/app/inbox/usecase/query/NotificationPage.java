package dev.patra.notification.app.inbox.usecase.query;

import java.util.List;

/// 提醒列表的一页。
///
/// @param items 提醒，排序时间新的在前
/// @param nextCursor 下一页的游标（不透明）；没有下一页为空
public record NotificationPage(List<NotificationView> items, String nextCursor) {

  /// 防御性拷贝。
  public NotificationPage {
    items = items == null ? List.of() : List.copyOf(items);
  }

  /// 构造。
  ///
  /// @param items 提醒
  /// @param nextCursor 下一页的游标，可空
  /// @return 一页
  public static NotificationPage of(List<NotificationView> items, String nextCursor) {
    return new NotificationPage(items, nextCursor);
  }
}
