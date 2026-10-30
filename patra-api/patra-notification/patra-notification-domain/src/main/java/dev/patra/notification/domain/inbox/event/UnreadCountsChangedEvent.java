package dev.patra.notification.domain.inbox.event;

import java.util.Collection;
import java.util.Set;

/// 这些人的未读数变了：写入了提醒、单条已读、全部已读、删除联动删掉了提醒。
///
/// 在信箱写入的事务里发布；订阅方（实时推送，PAP-145）用 `@TransactionalEventListener(AFTER_COMMIT)`，提交后按 ID
/// 经 `InboxReadPort.unreadCounts` 取数，回滚就收不到。保留期清理与注销清理不发。
///
/// @param recipientIds 未读数变了的收件人
public record UnreadCountsChangedEvent(Set<Long> recipientIds) {

  /// 防御性拷贝。
  public UnreadCountsChangedEvent {
    recipientIds = Set.copyOf(recipientIds);
  }

  /// 构造。
  ///
  /// @param recipientIds 收件人
  /// @return 事件
  public static UnreadCountsChangedEvent of(Collection<Long> recipientIds) {
    return new UnreadCountsChangedEvent(Set.copyOf(recipientIds));
  }
}
