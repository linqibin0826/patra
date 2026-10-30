package dev.patra.notification.domain.inbox.event;

import dev.patra.notification.domain.inbox.model.read.CreatedNotification;
import java.util.List;

/// 信箱里写入了新的提醒（只在真写入时发）。
///
/// 在写入的事务里发布；提醒邮件（PAP-146）据此排 10 分钟后的离站检查，用 `@TransactionalEventListener(AFTER_COMMIT)`
/// 订阅，回滚就收不到。
///
/// @param notifications 写入的提醒
public record NotificationsCreatedEvent(List<CreatedNotification> notifications) {

  /// 防御性拷贝。
  public NotificationsCreatedEvent {
    notifications = List.copyOf(notifications);
  }

  /// 构造。
  ///
  /// @param notifications 写入的提醒
  /// @return 事件
  public static NotificationsCreatedEvent of(List<CreatedNotification> notifications) {
    return new NotificationsCreatedEvent(notifications);
  }
}
