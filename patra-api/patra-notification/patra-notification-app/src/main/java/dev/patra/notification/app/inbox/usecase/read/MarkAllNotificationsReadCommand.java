package dev.patra.notification.app.inbox.usecase.read;

import dev.patra.notification.domain.inbox.model.read.UnreadCounts;
import dev.patra.tool.cqrs.Command;

/// 当前用户把三个标签的提醒全部标为已读。
public record MarkAllNotificationsReadCommand() implements Command<UnreadCounts> {

  /// 构造。
  ///
  /// @return 命令
  public static MarkAllNotificationsReadCommand of() {
    return new MarkAllNotificationsReadCommand();
  }
}
