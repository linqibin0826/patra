package dev.patra.notification.domain.inbox.event;

import dev.patra.notification.domain.inbox.model.read.CreatedNotification;
import java.util.List;

/// 信箱里写入了新的提醒（只在真写入时发）。
///
/// 在写入的事务里发布；提醒邮件（PAP-146）据此记「10 分钟后检查是否离站」的待办，是写库的订阅方，**用同步
/// `@EventListener` 在同一事务里写**（spec 10.2）：提交后再写的话，提交与写入之间进程一挂，这封邮件就永远丢了；同事务
/// 写，失败时整条消息回滚重试。时间窗口从 `CreatedNotification.createdAt` 算。
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
