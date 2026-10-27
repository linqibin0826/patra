package dev.patra.interaction.domain.notification.port.read;

import dev.patra.interaction.domain.notification.model.enums.NotificationTab;
import dev.patra.interaction.domain.notification.model.read.NotificationEntry;
import dev.patra.interaction.domain.notification.model.read.UnreadCounts;
import java.util.List;

/// 提醒的读端：收件人自己的列表与未读数。
public interface NotificationReadPort {

  /// 一个标签下的一页，新的在前。
  ///
  /// @param recipientId 收件人
  /// @param tab 标签
  /// @param beforeNotificationId 游标：只取 ID 比它小的；第一页为 `null`
  /// @param limit 条数
  /// @return 提醒
  List<NotificationEntry> page(
      long recipientId, NotificationTab tab, Long beforeNotificationId, int limit);

  /// 未读数。
  ///
  /// @param recipientId 收件人
  /// @return 两个标签各自的未读数
  UnreadCounts unreadCounts(long recipientId);
}
