package dev.patra.notification.app.inbox.usecase.read;

import dev.patra.notification.domain.inbox.model.read.UnreadCounts;
import dev.patra.tool.cqrs.Command;

/// 当前用户把一条提醒标为已读（点开即已读）。
///
/// @param notificationId 提醒 ID
public record MarkNotificationReadCommand(long notificationId) implements Command<UnreadCounts> {

  /// 构造。
  ///
  /// @param notificationId 提醒 ID
  /// @return 命令
  public static MarkNotificationReadCommand of(long notificationId) {
    return new MarkNotificationReadCommand(notificationId);
  }
}
