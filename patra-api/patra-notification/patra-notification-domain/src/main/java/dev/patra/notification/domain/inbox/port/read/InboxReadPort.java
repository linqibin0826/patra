package dev.patra.notification.domain.inbox.port.read;

import dev.patra.notification.domain.inbox.model.read.InboxEntry;
import dev.patra.notification.domain.inbox.model.read.UnreadCounts;
import dev.patra.notification.domain.inbox.model.vo.InboxCursor;
import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
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
}
