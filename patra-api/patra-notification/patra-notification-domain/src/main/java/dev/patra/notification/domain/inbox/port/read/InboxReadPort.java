package dev.patra.notification.domain.inbox.port.read;

import dev.patra.notification.domain.inbox.model.read.InboxCursor;
import dev.patra.notification.domain.inbox.model.read.InboxEntry;
import dev.patra.notification.domain.inbox.model.read.UnreadCounts;
import dev.patra.notification.domain.inbox.model.read.UnreadWindow;
import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/// 信箱的读端：收件人自己的列表与未读数。实时推送（PAP-145）、提醒邮件（PAP-146）也经它读信箱。
public interface InboxReadPort {

  /// 一个类别下的一页，排序时间新的在前，同一时刻按 ID 倒序。
  ///
  /// @param recipientId 收件人
  /// @param category 类别
  /// @param after 游标：只取排在它后面的；第一页为 `null`
  /// @param limit 条数
  /// @return 提醒，带评论最新版本的纯文本
  List<InboxEntry> page(
      long recipientId, NotificationCategory category, InboxCursor after, int limit);

  /// 三个类别各自的未读数。
  ///
  /// @param recipientId 收件人
  /// @return 未读数
  UnreadCounts unreadCounts(long recipientId);

  /// 给定的提醒里仍存在且未读的（提醒邮件到点复查用）。
  ///
  /// @param recipientId 收件人；别人的提醒不返回
  /// @param notificationIds 提醒 ID
  /// @return 提醒，按排序时间先后、同一时刻按 ID
  List<InboxEntry> findUnread(long recipientId, Collection<Long> notificationIds);

  /// 一个类别在写入时刻窗口 `[from, to)` 里的未读（每日汇总用）。
  ///
  /// @param recipientId 收件人
  /// @param category 类别
  /// @param from 窗口左端（含），按写入信箱的时刻
  /// @param to 窗口右端（不含）
  /// @param limit 最多取几条
  /// @return 未读总数与排序时间最新的几条
  UnreadWindow unreadBetween(
      long recipientId, NotificationCategory category, Instant from, Instant to, int limit);
}
